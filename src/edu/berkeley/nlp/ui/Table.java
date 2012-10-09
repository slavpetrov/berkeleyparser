package edu.berkeley.nlp.ui;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.Map;

import edu.berkeley.nlp.util.Counter;
import edu.berkeley.nlp.util.Pair;
import edu.berkeley.nlp.util.StrUtils;

public class Table {
	// row -> column -> entry
	private final Map<Pair<Integer, Integer>, String[]> entries = new HashMap<Pair<Integer, Integer>, String[]>();
	private final Counter<Integer> maxHeights = new Counter<Integer>(),
			maxWidths = new Counter<Integer>();
	private int nColumns, nRows;

	public Table(Populator populator) {
		populator.setTable(this);
		populator.populate();
	}

	public static abstract class Populator {
		private void setTable(Table table) {
			this.table = table;
		}

		private Table table;
		private DecimalFormat fmt = EasyFormat.getStdFormat();

		public Populator() {
		}

		public Populator(DecimalFormat fmt) {
			this.fmt = fmt;
		}

		public abstract void populate();

		public final void set(int row, int column, double number) {
			set(row, column, fmt.format(number));
		}

		public final void set(int row, int column, String text) {
			table.nColumns = Math.max(table.nColumns, column + 1);
			table.nRows = Math.max(table.nRows, row + 1);
			String[] lines = text.split("\\n");
			table.maxHeights.setCount(row,
					Math.max(table.maxHeights.getCount(row), lines.length));
			for (String line : lines)
				table.maxWidths.setCount(
						column,
						Math.max(table.maxWidths.getCount(column),
								line.length()));
			table.entries.put(new Pair<Integer, Integer>(row, column), lines);
		}

		/** append at the end of the last line */
		public final void append(int row, int column, String text) {
			String[] entries = table.entries.get(new Pair<Integer, Integer>(
					row, column));
			String lastEntry = (entries != null ? entries[entries.length - 1]
					: null);
			set(row, column, (lastEntry != null ? lastEntry : "") + text);
		}

		public final void addLines(int row, int column, String text) {
			Pair<Integer, Integer> key = new Pair<Integer, Integer>(row, column);
			String currentString = StrUtils.join(table.entries.get(key), "\n");
			if (!currentString.equals(""))
				currentString = currentString + "\n";
			currentString += text;
			set(row, column, currentString);
		}
	}

	private boolean borderDefault = true;

	public void setBorder(boolean value) {
		this.borderDefault = value;
	}

	@Override
	public String toString() {
		return toString(borderDefault);
	}

	public String toHTML() {
		return toHTML(borderDefault);
	}

	public String toHTML(boolean printBorders) {
		StringBuilder builder = new StringBuilder();
		builder.append("<table class=\""
				+ (printBorders ? "with-borders" : "without-borders") + "\">");
		for (int row = 0; row < nRows; row++) {
			builder.append("<tr>");
			for (int col = 0; col < nColumns; col++) {
				builder.append("<td>");
				Pair<Integer, Integer> key = new Pair<Integer, Integer>(row,
						col);
				String[] entry = entries.get(key);
				if (entry != null)
					for (int i = 0; i < entry.length; i++)
						builder.append(entry[i]
								+ (i < entry.length - 1 ? "<br/>" : "") + "");
				builder.append("</td>");
			}
			builder.append("</tr>");
		}
		builder.append("</table>\n");
		return builder.toString();
	}

	public static final String css = "table.with-borders {"
			+ "border-width: 1px 1px 1px 1px;" + "border-spacing: 2px;"
			+ "border-style: solid solid solid solid;"
			+ "border-color: black black black black;"
			+ "border-collapse: collapse;" + "background-color: white;" + "}"
			+ "table.with-borders td {" + "border-width: 1px 1px 1px 1px;"
			+ "padding: 1px 1px 1px 1px;"
			+ "border-style: dotted dotted dotted dotted;"
			+ "border-color: gray gray gray gray;" + "background-color: white;"
			+ "-moz-border-radius: 0px 0px 0px 0px;" + "}"
			+ "table.without-borders {" + "border-width: 1px 1px 1px 1px;"
			+ "border-spacing: 2px;" + "border-style: none none none none;"
			+ "border-collapse: collapse;" + "background-color: white;" + "}"
			+ "table.without-borders td {" + "border-width: 1px 1px 1px 1px;"
			+ "padding: 1px 1px 1px 1px;"
			+ "border-style: none none none none;" + "background-color: white;"
			+ "-moz-border-radius: 0px 0px 0px 0px;" + "}";

	public String toString(boolean printBorders) {
		StringBuilder builder = new StringBuilder();
		// top border
		if (printBorders)
			builder.append(horizontalSeparator() + "\n");
		for (int row = 0; row < nRows; row++) {
			for (int rowLine = 0; rowLine < maxHeights.getCount(row); rowLine++) {
				// left border
				if (printBorders)
					builder.append("|");
				for (int col = 0; col < nColumns; col++) {
					Pair<Integer, Integer> key = new Pair<Integer, Integer>(
							row, col);
					String text = (entries.get(key) == null
							|| rowLine >= entries.get(key).length ? ""
							: entries.get(key)[rowLine]);
					builder.append(pad(text, (int) maxWidths.getCount(col), " "));
					if (printBorders)
						builder.append("|");
				}
				if (rowLine != maxHeights.getCount(row) - 1)
					builder.append("\n");
			}
			if (printBorders)
				builder.append("\n" + horizontalSeparator());
			builder.append("\n");
		}
		return builder.toString();
	}

	private StringBuilder horizontalSeparator() {
		StringBuilder builder = new StringBuilder();
		builder.append("+");
		for (int col = 0; col < nColumns; col++)
			builder.append(pad("", (int) maxWidths.getCount(col), "-") + "+");
		return builder;
	}

	private String pad(String s, int finalLength, String pad) {
		if (s.length() > finalLength)
			throw new RuntimeException();
		StringBuilder b = new StringBuilder();
		b.append(s);
		while (b.length() < finalLength)
			b.append(pad);
		return b.toString();
	}

	public static void main(String[] args) {
		Table table = new Table(new Populator() {
			@Override
			public void populate() {
				for (int i = 0; i < 10; i++)
					for (int j = 0; j < 10; j++)
						if (i <= j)
							addLines(i, j, "Sum:" + (i + j));
				for (int i = 0; i < 10; i++)
					for (int j = 0; j < 10; j++)
						if (i <= j)
							addLines(i, j, "Prod:" + (i * j));
			}
		});
		System.out.println(table.toString());
		// System.out.println(table.toHTML(false));
	}
}
