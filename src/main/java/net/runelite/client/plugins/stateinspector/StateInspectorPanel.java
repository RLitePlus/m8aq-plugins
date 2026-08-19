package net.runelite.client.plugins.stateinspector;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

final class StateInspectorPanel extends PluginPanel
{
	private static final long serialVersionUID = 1L;

	private final List<Class<?>> classes;
	private final JComboBox<String> classPicker;
	private final DefaultTableModel tableModel = new DefaultTableModel(new Object[]{"Method", "Value"}, 0);
	private final Consumer<Class<?>> refresh;
	private final JTextArea expandedValue = new JTextArea();
	private volatile Class<?> selectedClass;
	private volatile boolean active;
	private JDialog valueDialog;

	StateInspectorPanel(List<Class<?>> classes, Consumer<Class<?>> refresh)
	{
		super(false);
		this.classes = classes;
		this.refresh = refresh;
		selectedClass = classes.get(0);
		setBorder(new EmptyBorder(BORDER_OFFSET, BORDER_OFFSET, BORDER_OFFSET, BORDER_OFFSET));
		setLayout(new BorderLayout(0, BORDER_OFFSET));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		classPicker = new JComboBox<>(classes.stream().map(Class::getSimpleName).toArray(String[]::new));
		classPicker.addActionListener(event ->
		{
			selectedClass = classes.get(classPicker.getSelectedIndex());
			refresh.accept(selectedClass);
		});

		JPanel controls = new JPanel(new BorderLayout(BORDER_OFFSET, 0));
		controls.setBackground(ColorScheme.DARK_GRAY_COLOR);
		controls.add(classPicker, BorderLayout.CENTER);
		add(controls, BorderLayout.NORTH);

		JTable table = new JTable(tableModel);
		table.setDefaultEditor(Object.class, null);
		table.setToolTipText("Double-click a value to expand it");
		table.getColumnModel().getColumn(0).setPreferredWidth(90);
		table.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent event)
			{
				int row = table.rowAtPoint(event.getPoint());
				int column = table.columnAtPoint(event.getPoint());
				if (event.getClickCount() == 2 && SwingUtilities.isLeftMouseButton(event)
					&& row >= 0 && column >= 0 && table.convertColumnIndexToModel(column) == 1)
				{
					showExpandedValue(String.valueOf(table.getValueAt(row, 0)),
						String.valueOf(table.getValueAt(row, column)));
				}
			}
		});
		add(new JScrollPane(table), BorderLayout.CENTER);

		expandedValue.setEditable(false);
		expandedValue.setLineWrap(true);
		expandedValue.setWrapStyleWord(false);
	}

	Class<?> selectedClass()
	{
		return selectedClass;
	}

	boolean isActive()
	{
		return active;
	}

	@Override
	public void onActivate()
	{
		active = true;
		refresh.accept(selectedClass);
	}

	@Override
	public void onDeactivate()
	{
		active = false;
	}

	void showValues(Map<String, String> values)
	{
		assert SwingUtilities.isEventDispatchThread();
		tableModel.setRowCount(0);
		values.forEach((name, value) -> tableModel.addRow(new Object[]{name, value}));
	}

	void showError(Throwable throwable)
	{
		showValues(Collections.singletonMap("error", throwable.toString()));
	}

	void dispose()
	{
		if (valueDialog != null)
		{
			valueDialog.dispose();
			valueDialog = null;
		}
	}

	private void showExpandedValue(String name, String value)
	{
		assert SwingUtilities.isEventDispatchThread();
		expandedValue.setText(value);
		expandedValue.setCaretPosition(0);

		if (valueDialog == null)
		{
			Window owner = SwingUtilities.getWindowAncestor(this);
			valueDialog = new JDialog(owner, "State Inspector", Dialog.ModalityType.MODELESS);
			valueDialog.setDefaultCloseOperation(JDialog.HIDE_ON_CLOSE);
			JScrollPane scrollPane = new JScrollPane(expandedValue);
			scrollPane.setPreferredSize(new Dimension(700, 400));
			valueDialog.add(scrollPane);
			valueDialog.pack();
			valueDialog.setLocationRelativeTo(this);
		}

		valueDialog.setTitle("State Inspector — " + name);
		valueDialog.setVisible(true);
		valueDialog.toFront();
	}
}
