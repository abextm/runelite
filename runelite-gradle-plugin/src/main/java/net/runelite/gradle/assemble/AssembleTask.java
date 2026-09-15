/*
 * Copyright (c) 2024, LlemonDuck <napkinorton@gmail.com>
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

package net.runelite.gradle.assemble;

import com.google.common.io.Files;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileSystemOperations;
import org.gradle.api.logging.Logger;
import org.gradle.api.logging.Logging;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkerExecutor;

@CacheableTask
public abstract class AssembleTask extends DefaultTask
{
	@Classpath
	public abstract ConfigurableFileCollection getCacheClasspath();

	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getScriptDirectory();

	@OutputDirectory
	public abstract DirectoryProperty getOutputDirectory();

	@Input
	public abstract Property<Boolean> getLongSupport();

	@Inject
	protected abstract WorkerExecutor getWorkerExecutor();

	@TaskAction
	public void assembleRs2Asm() throws IOException
	{
		getWorkerExecutor()
			.classLoaderIsolation(s -> s.getClasspath().from(getCacheClasspath()))
			.submit(Action.class, p ->
			{
				p.getScriptDirectory().set(getScriptDirectory());
				p.getOutputDirectory().set(getOutputDirectory());
				p.getLongSupport().set(getLongSupport());
			});
	}

	public interface Params extends WorkParameters
	{
		public abstract DirectoryProperty getScriptDirectory();

		public abstract DirectoryProperty getOutputDirectory();

		public abstract Property<Boolean> getLongSupport();
	}

	public abstract static class Action implements WorkAction<Params>
	{
		private static final Logger log = Logging.getLogger(Action.class);

		@Inject
		protected abstract FileSystemOperations getFileSystemOperations();

		@Override
		public void execute()
		{
			try
			{
				var params = getParameters();

				File scriptDirectory = params.getScriptDirectory().getAsFile().get();
				File outputDirectory = params.getOutputDirectory().getAsFile().get();

				var cl = Action.class.getClassLoader();

				var clRLInstruction = cl.loadClass("net.runelite.cache.script.RuneLiteInstructions");
				var instructions = clRLInstruction.getConstructor().newInstance();
				instructions.getClass().getMethod("init").invoke(instructions);

				var clInstruction = cl.loadClass("net.runelite.cache.script.Instructions");
				var clAssembler = cl.loadClass("net.runelite.cache.script.assembler.Assembler");
				var assembler = clAssembler.getConstructor(clInstruction).newInstance(instructions);
				var assemblerAssemble = clAssembler.getMethod("assemble", InputStream.class);

				var clScriptDefinition = cl.loadClass("net.runelite.cache.definitions.ScriptDefinition");
				var scriptDefinitionGetId = clScriptDefinition.getMethod("getId");

				var clScriptSaver = cl.loadClass("net.runelite.cache.definitions.savers.ScriptSaver");
				var saver = clScriptSaver.getConstructor(boolean.class).newInstance(params.getLongSupport().getOrElse(true));
				var saverSave = clScriptSaver.getMethod("save", clScriptDefinition);

				int count = 0;
				File scriptOut = new File(outputDirectory, "12");

				getFileSystemOperations().delete(e -> e.delete(scriptOut));
				scriptOut.mkdirs();

				for (File scriptFile : scriptDirectory.listFiles((dir, name) -> name.endsWith(".rs2asm")))
				{
					log.debug("Assembling {}", scriptFile);

					try (FileInputStream fin = new FileInputStream(scriptFile))
					{
						var script = assemblerAssemble.invoke(assembler, fin);
						byte[] packedScript = (byte[]) saverSave.invoke(saver, script);

						int id = (Integer) scriptDefinitionGetId.invoke(script);

						File targetFile = new File(scriptOut, "" + id);
						Files.write(packedScript, targetFile);

						// Copy hash file

						File hashFile = new File(scriptDirectory, Files.getNameWithoutExtension(scriptFile.getName()) + ".hash");
						if (hashFile.exists())
						{
							Files.copy(hashFile, new File(scriptOut, id + ".hash"));
						}
						else if (id < 10000) // Scripts >=10000 are RuneLite scripts, so they shouldn't have a .hash
						{
							throw new FileNotFoundException("Unable to find hash file for " + scriptFile);
						}

						++count;
					}
				}

				log.lifecycle("Assembled {} scripts", count);
			}
			catch (Exception e)
			{
				throw new GradleException(e.getMessage(), e);
			}
		}
	}
}
