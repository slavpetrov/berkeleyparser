//package edu.berkeley.nlp.auxv;
//
//import nuts.tui.Table;
//
//public class HTMLRenderer
//{
//  private StringBuilder builder = new StringBuilder();
//  public void addItem(String contents)
//  {
//    builder.append("<li>" + contents + "</li>\n");
//  }
//  public void addItem(String contents, String description)
//  {
//    builder.append("<li><emph>" + description + ":</emph>" + contents + "</li>\n");
//  }
//  public void addItem(Table table, String description)
//  {
//    builder.append("<li><emph>" + description + ":</emph><br/>" + table.toHTML() + "</li>\n");
//  }
//  public StringBuilder getHTMLPage()
//  {
//    StringBuilder result = new StringBuilder();
//    result.insert(0, "<html><head><style type=\"text/css\">" + Table.css + "</style></head><body>\n" +
//        "<ul>\n");
//    result.append(builder);
//    result.append("\n" +
//        "</ul>\n" +
//        "</body></html>");
//    return result;
//  }
//  public void indent(String title) 
//  {
//    builder.append("<li><emph>" + title + ":</emph>\n" +
//        "<ul>");
//  }
//  public void unIndent()
//  {
//    builder.append("</ul></li>");
//  }
//}
