package net.runelite.client.ui.components;

import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DNDTabbedPane extends JTabbedPane
{
	public static final String TAB_ID = "runelite.tabID";

	private static final String uiClassID = "DNDTabbedPaneUI";

	@Getter
	@Setter
	private Predicate<MouseEvent> dragPredicate = ev -> ev.isControlDown();

	@Getter
	@Setter
	private List<String> tabOrder = new ArrayList<>();

	public DNDTabbedPane(int tabPlacement)
	{
		super(tabPlacement);
	}

	@Override
	public String getUIClassID()
	{
		return uiClassID;
	}

	public void insertDraggableTab(String title, Icon icon, Component component, String tip, String id, int index)
	{
		((JComponent) component).putClientProperty(TAB_ID, id);
		insertTab(title, icon, component, tip, index);
	}

	@AllArgsConstructor
	@Getter
	public static class Tab
	{
		private Component component;

		@Setter
		private float priority;
	}
}
