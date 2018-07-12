/*
 * Copyright (c) 2018 Abex
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.api;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.SetMultimap;
import java.lang.invoke.CallSite;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import lombok.EqualsAndHashCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class EventBus
{
	/**
	 * This is a internal interface for the EventBus and is not part of it's public API
	 * It is marked public because the generated lambda needs access to it
	 */
	@FunctionalInterface
	public interface SubscriberMethod
	{
		void invoke(Object self, Object event);
	}

	@RequiredArgsConstructor
	private static class Subscriber
	{
		private final Object self;

		// This exists primarily for equals and hashcode
		private final Method method;

		@Nullable
		@EqualsAndHashCode.Exclude
		private final SubscriberMethod lambda;

		public void invoke(Object event) throws Throwable
		{
			if (lambda != null)
			{
				lambda.invoke(this.self, event);
			}
			else
			{
				try
				{
					method.invoke(this.self, event);
				}
				catch (InvocationTargetException e)
				{
					throw e.getCause();
				}
				catch (ReflectiveOperationException e)
				{
					throw new RuntimeException(e);
				}
			}
		}
	}

	private static class OneShotSet
	{
		private List<Predicate> subscribers = null;
		// Recycler
		private List<Predicate> oldSubscribers = null;

		public synchronized void add(Predicate event)
		{
			if (subscribers == null)
			{
				subscribers = oldSubscribers;
				oldSubscribers = null;
				if (subscribers == null)
				{
					subscribers = new ArrayList<>();
				}
			}
			subscribers.add(event);
		}

		public void invoke(Object event)
		{
			List<Predicate> events;
			synchronized (this)
			{
				events = subscribers;
				subscribers = null;
			}

			if (events == null)
			{
				return;
			}

			for (Predicate os : events)
			{
				boolean remove = true;
				try
				{
					remove = os.test(event);
				}
				catch (ThreadDeath | VirtualMachineError d)
				{
					throw d;
				}
				catch (Throwable e)
				{
					log.error("Exception thrown in oneshot event", e);
				}
				finally
				{
					if (!remove)
					{
						add(os);
					}
				}
			}

			synchronized (this)
			{
				// If the old list is small enough, recycle it
				if (events.size() < 32 || (subscribers != null && events.size() < subscribers.size() + 16))
				{
					events.clear();
					oldSubscribers = events;
				}
			}
		}
	}

	private final Object subscribersLock = new Object();
	private final SetMultimap<Object, Subscriber> synSubscribersByOwner = HashMultimap.create();
	private final SetMultimap<Class, Subscriber> synSubscribersByEvent = HashMultimap.create();
	private SetMultimap<Class, Subscriber> tsSubscribersByEvent = null;

	private final Object oneShotsLock = new Object();
	private final Map<Class, OneShotSet> synOneShots = new HashMap<>();
	private Map<Class, OneShotSet> tsOneShots = null;

	private final Object debugLock;
	private final Map<Class, Integer> synPostCounts;
	private int numPosts = 0;

	/**
	 * Creates an EventBus without statistics
	 */
	public EventBus()
	{
		this(false);
	}

	/**
	 * @param statistics If true this EventBus will print statistics about it's usage every 25k posted events
	 */
	public EventBus(boolean statistics)
	{
		if (statistics)
		{
			debugLock = new Object();
			synPostCounts = new HashMap<>();
		}
		else
		{
			debugLock = null;
			synPostCounts = null;
		}
	}

	/**
	 * Adds an object with `@Subscribe`d methods to the EventBus.
	 */
	public void register(final Object object)
	{
		for (Class<?> clazz = object.getClass(); clazz != null; clazz = clazz.getSuperclass())
		{
			for (final Method method : clazz.getDeclaredMethods())
			{
				Subscribe sub = method.getAnnotation(Subscribe.class);
				if (sub == null)
				{
					continue;
				}

				assert method.getReturnType() == Void.TYPE : "@Subscribed methods cannot return a value";
				assert method.getParameterCount() == 1 : "@Subscribed methods must take exactly 1 argument";
				assert !Modifier.isStatic(method.getModifiers()) : "@Subscribed methods cannot be static";
				final Class<?> parameterClazz = method.getParameterTypes()[0];
				assert !parameterClazz.isPrimitive() : "@Subscribed methods cannot subscribe to primitives";

				{
					String preferredName = "on" + parameterClazz.getSimpleName();
					assert method.getName().equals(preferredName) : "Subscribed method " + method + " should be named " + preferredName;
				}

				method.setAccessible(true);

				SubscriberMethod lambda = null;
				try
				{
					MethodHandles.Lookup lookup = getLookupForClass(clazz);

					final MethodHandle subscriberHandle = lookup.unreflectSpecial(method, clazz);

					final CallSite callSite = LambdaMetafactory.metafactory(
						lookup,
						"invoke",
						MethodType.methodType(SubscriberMethod.class),
						MethodType.methodType(void.class, Object.class, Object.class),
						subscriberHandle,
						MethodType.methodType(void.class, clazz, parameterClazz)
					);

					final MethodHandle lambdaFactory = callSite.getTarget();
					lambda = (SubscriberMethod) lambdaFactory.invoke();
				}
				catch (ThreadDeath | VirtualMachineError d)
				{
					throw d;
				}
				catch (Throwable e) // Yes, this literally `throws Throwable`
				{
					log.info("Unable to create lambda for method {}", method, e);
				}

				final Subscriber subscriber = new Subscriber(object, method, lambda);

				final Class<?>[] children;
				{
					boolean isFinal = Modifier.isFinal(parameterClazz.getModifiers());
					ChildEvents childEvents = parameterClazz.getAnnotation(ChildEvents.class);
					if (childEvents == null)
					{
						children = null;

						if (!isFinal)
						{
							log.warn("Non final event without @ChildEvents: {}", parameterClazz);
						}
					}
					else
					{
						children = childEvents.value();

						if (isFinal)
						{
							log.warn("Final event with @ChildEvents: {}", parameterClazz);
						}

						for (Class<?> child : children)
						{
							assert parameterClazz.isAssignableFrom(child);
						}
					}
				}

				synchronized (subscribersLock)
				{
					if (synSubscribersByOwner.put(object, subscriber))
					{
						synSubscribersByEvent.put(parameterClazz, subscriber);
						if (children != null)
						{
							for (Class<?> child : children)
							{
								synSubscribersByEvent.put(child, subscriber);
							}
						}
						tsSubscribersByEvent = null;
					}
				}
			}
		}
	}

	/**
	 * Removes a object previously registered with {@link #register(Object)} from
	 * the EventBus
	 */
	public void unregister(Object o)
	{
		synchronized (subscribersLock)
		{
			synSubscribersByEvent.values().removeAll(synSubscribersByOwner.removeAll(o));
			tsSubscribersByEvent = null;
		}
	}


	/**
	 * Runs {@code once} next time a {@code eventClass} is posted to the event bus.
	 */
	public <E> void once(Class<E> eventClass, Consumer<E> once)
	{
		onceOrMore(eventClass, (E e) ->
		{
			once.accept(e);
			return true;
		});
	}

	/**
	 * Runs {@code once} next time a {@code eventClass} is posted to the event bus.
	 * if {@code once} returns false it will be requeued for the a later event
	 */
	public <E> void onceOrMore(Class<E> eventClass, Predicate<E> once)
	{
		OneShotSet oss;
		synchronized (oneShotsLock)
		{
			oss = synOneShots.get(eventClass);
			if (oss == null)
			{
				oss = new OneShotSet();
				synOneShots.put(eventClass, oss);
				tsOneShots = null;
			}
		}
		oss.add(once);
	}

	/**
	 * Posts an event to the event bus. All subscriber methods will be ran
	 * on this thread sequentially. Any {@code once}/{@code onceOreMore}s
	 * added during event processing will not be ran during this event post
	 */
	public void post(final Object object)
	{
		SetMultimap<Class, Subscriber> subscribers = tsSubscribersByEvent;
		if (subscribers == null)
		{
			synchronized (subscribersLock)
			{
				if (tsSubscribersByEvent == null)
				{
					tsSubscribersByEvent = ImmutableSetMultimap.copyOf(synSubscribersByEvent);
				}
				subscribers = tsSubscribersByEvent;
			}
		}

		Map<Class, OneShotSet> oneShots = tsOneShots;
		if (oneShots == null)
		{
			synchronized (oneShotsLock)
			{
				if (tsOneShots == null)
				{
					tsOneShots = ImmutableMap.copyOf(synOneShots);
				}
				oneShots = tsOneShots;
			}
		}

		final Class<?> objectClazz = object.getClass();

		final OneShotSet oss = oneShots.get(objectClazz);
		if (oss != null)
		{
			oss.invoke(object);
		}

		final Set<Subscriber> evSubs = subscribers.get(objectClazz);
		for (Subscriber c : evSubs)
		{
			try
			{
				c.invoke(object);
			}
			catch (ThreadDeath | VirtualMachineError d)
			{
				throw d;
			}
			catch (Throwable e)
			{
				log.error("Exception thrown in event subscriber", e);
			}
		}

		if (debugLock != null)
		{
			synchronized (debugLock)
			{
				synPostCounts.compute(objectClazz, (k, v) -> v == null ? 1 : v + 1);
				if (++numPosts % 25000 == 0)
				{
					synchronized (subscribersLock)
					{
						log.debug("Event bus statistics:");
						log.debug("{} posts", numPosts);
						log.debug("{} subscribed objects", synSubscribersByOwner.keySet().size());
						log.debug("{} subscribed methods", synSubscribersByOwner.size());
						log.debug("{} event types", synSubscribersByEvent.keySet().size());
						synPostCounts.entrySet().stream()
							.sorted(Comparator.comparing(e -> -e.getValue()))
							.forEach(e -> log.debug("{} - {}", e.getValue(), e.getKey()));
					}
				}
			}
		}
	}

	private static MethodHandles.Lookup getLookupForClass(Class clazz)
		throws InvocationTargetException, IllegalAccessException, NoSuchFieldException
	{
		// There are 2 ways to do this:
		// a) The Java 9+ way, with privateLookupIn. I prefer this because it is the public API.
		//    This is done using reflection because we have to compile on java 8
		// b) The Java 8 way, by accessing a internal field that elides some access checks
		//    This is done using reflection so we can disable access checks
		// https://stackoverflow.com/a/28188483
		try
		{
			Method privateLookupIn = MethodHandles.class.getMethod("privateLookupIn", Class.class, MethodHandles.Lookup.class);
			return (MethodHandles.Lookup) privateLookupIn.invoke(null, clazz, MethodHandles.lookup());
		}
		catch (NoSuchMethodException e)
		{
			Field IMPL_LOOKUP = MethodHandles.Lookup.class.getDeclaredField("IMPL_LOOKUP");
			IMPL_LOOKUP.setAccessible(true);
			MethodHandles.Lookup implLookup = (MethodHandles.Lookup) IMPL_LOOKUP.get(null);
			return implLookup.in(clazz);
		}
	}
}
