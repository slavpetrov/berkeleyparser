/**
 * 
 */
package edu.berkeley.nlp.ui;

import javax.swing.*;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.RenderedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringReader;

import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import javax.swing.*;

import java.awt.*;
import java.awt.image.*;

import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees;

/**
 * Class for displaying a Tree.
 *
 * @author Dan Klein
 */

public class TreeJPanel extends JPanel {

  int VERTICAL_ALIGN = SwingConstants.CENTER;
  int HORIZONTAL_ALIGN = SwingConstants.CENTER;

  int maxFontSize = 10;
  int minFontSize = 2;

  int preferredX = 400;
  int preferredY = 300;

  double sisterSkip = 2.5;
  double parentSkip = 0.5;
  double belowLineSkip = 0.1;
  double aboveLineSkip = 0.1;
  
  FontMetrics myFont;

  Tree<String> tree;

  public Tree<String> getTree() {
    return tree;
  }

  public void setTree(Tree<String> tree) {
    this.tree = tree;
    repaint();
  }

  String nodeToString(Tree<String> t) {
    if (t == null) {
      return " ";
    }
    Object l = t.getLabel();
    if (l == null) {
      return " ";
    }
    String str = (String)l;
    if (str == null) {
      return " ";
    }
    return str;
  }

  static class WidthResult {
    double width = 0.0;
    double nodeTab = 0.0;
    double nodeCenter = 0.0;
    double childTab = 0.0;
  }

  double width(Tree<String> tree, FontMetrics fM) {
    return widthResult(tree, fM).width;
  }

  public int width() {
    return (int)widthResult(getTree(), myFont).width;
  }
  
  WidthResult wr = new WidthResult();

  WidthResult widthResult(Tree<String> tree, FontMetrics fM) {
    if (tree == null) {
      wr.width = 0.0;
      wr.nodeTab = 0.0;
      wr.nodeCenter = 0.0;
      wr.childTab = 0.0;
      return wr;
    }
    double local = fM.stringWidth(nodeToString(tree));
    if (tree.isLeaf()) {
      wr.width = local;
      wr.nodeTab = 0.0;
      wr.nodeCenter = local / 2.0;
      wr.childTab = 0.0;
      return wr;
    }
    double sub = 0.0;
    double nodeCenter = 0.0;
    double childTab = 0.0;
    for (int i = 0; i < tree.getChildren().size(); i++) {
      WidthResult subWR = widthResult((Tree<String>)tree.getChildren().get(i), fM);
      if (i == 0) {
        nodeCenter += (sub + subWR.nodeCenter) / 2.0;
      }
      if (i == tree.getChildren().size() - 1) {
        nodeCenter += (sub + subWR.nodeCenter) / 2.0;
      }
      sub += subWR.width;
      if (i < tree.getChildren().size() - 1) {
        sub += sisterSkip * fM.stringWidth(" ");
      }
    }
    double localLeft = local / 2.0;
    double subLeft = nodeCenter;
    double totalLeft = Math.max(localLeft, subLeft);
    double localRight = local / 2.0;
    double subRight = sub - nodeCenter;
    double totalRight = Math.max(localRight, subRight);
    wr.width = totalLeft + totalRight;
    wr.childTab = totalLeft - subLeft;
    wr.nodeTab = totalLeft - localLeft;
    wr.nodeCenter = nodeCenter + wr.childTab;
    return wr;
  }

  double height(Tree<String> tree, FontMetrics fM) {
    if (tree == null) {
      return 0.0;
    }
    double depth = tree.getDepth();
    //double f = fM.getHeight() ;
    return fM.getHeight() * ( depth * (1.0 + parentSkip + aboveLineSkip + belowLineSkip)-parentSkip);
  }

  public int height() {
  	return (int)height(getTree(),myFont);
  }

  FontMetrics pickFont(Graphics2D g2, Tree<String> tree, Dimension space) {
    Font font = g2.getFont();
    String name = font.getName();
    int style = font.getStyle();

    for (int size = maxFontSize; size > minFontSize; size--) {
      font = new Font(name, style, size);
      g2.setFont(font);
      FontMetrics fontMetrics = g2.getFontMetrics();
      if (height(tree, fontMetrics) > space.getHeight()) {
        continue;
      }
      if (width(tree, fontMetrics) > space.getWidth()) {
        continue;
      }
      //System.out.println("Chose: "+size+" for space: "+space.getWidth());
      return fontMetrics;
    }
    font = new Font(name, style, minFontSize);
    g2.setFont(font);
    return g2.getFontMetrics();
  }

  double paintTree(Tree<String> t, Point2D start, Graphics2D g2, FontMetrics fM) {
    if (t == null) {
      return 0.0;
    }
    String nodeStr = nodeToString(t);
    double nodeWidth = fM.stringWidth(nodeStr);
    double nodeHeight = fM.getHeight();
    double nodeAscent = fM.getAscent();
    WidthResult wr = widthResult(t, fM);
    double treeWidth = wr.width;
    double nodeTab = wr.nodeTab;
    double childTab = wr.childTab;
    double nodeCenter = wr.nodeCenter;
    // draw root
    g2.drawString(nodeStr, (float) (nodeTab + start.getX()), (float) (start.getY() + nodeAscent));
    if (t.isLeaf()) {
      return nodeWidth;
    }
    double layerMultiplier = (1.0 + belowLineSkip + aboveLineSkip + parentSkip);
    double layerHeight = nodeHeight * layerMultiplier;
    double childStartX = start.getX() + childTab;
    double childStartY = start.getY() + layerHeight;
    double lineStartX = start.getX() + nodeCenter;
    double lineStartY = start.getY() + nodeHeight * (1.0 + belowLineSkip);
    double lineEndY = lineStartY + nodeHeight * parentSkip;
    // recursively draw children
    for (int i = 0; i < t.getChildren().size(); i++) {
      Tree<String> child = (Tree<String>)t.getChildren().get(i);
      double cWidth = paintTree(child, new Point2D.Double(childStartX, childStartY), g2, fM);
      // draw connectors
      wr = widthResult(child, fM);
      double lineEndX = childStartX + wr.nodeCenter;
      g2.draw(new Line2D.Double(lineStartX, lineStartY, lineEndX, lineEndY));
      childStartX += cWidth;
      if (i < t.getChildren().size() - 1) {
        childStartX += sisterSkip * fM.stringWidth(" ");
      }
    }
    return treeWidth;
  }

  	@Override
  	public void repaint()
  	{
  		super.repaint();
  	}
	@Override
	public void paint(Graphics g)
	{
		g.clearRect(0, 0, getWidth(), getHeight());
		((Graphics2D) g).setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
		this.paintComponent(g);
	}

	@Override
	public void paintComponent(Graphics g) {
		Graphics2D g2 = (Graphics2D) g;
		//FontMetrics fM = pickFont(g2, tree, space);

		double width = width(tree, myFont);
		double height = height(tree, myFont);
		preferredX = (int)width;
		preferredY = (int)height;
		setSize(new Dimension(preferredX, preferredY));
		setPreferredSize(new Dimension(preferredX, preferredY));
		setMaximumSize(new Dimension(preferredX, preferredY));
		setMinimumSize(new Dimension(preferredX, preferredY));
		//setSize(new Dimension((int)Math.round(width), (int)Math.round(height)));
		g2.setFont(myFont.getFont());

		Dimension space = getSize();
		double startX = 0.0;
		double startY = 0.0;
		if (HORIZONTAL_ALIGN == SwingConstants.CENTER) {
			startX = (space.getWidth() - width) / 2.0;
		}
		if (HORIZONTAL_ALIGN == SwingConstants.RIGHT) {
			startX = space.getWidth() - width;
		}
		if (VERTICAL_ALIGN == SwingConstants.CENTER) {
			startY = (space.getHeight() - height) / 2.0;
		}
		if (VERTICAL_ALIGN == SwingConstants.BOTTOM) {
			startY = space.getHeight() - height;
		}
		super.paintComponent(g);

		g2.setBackground(Color.white);
		g2.clearRect(0, 0, space.width, space.height);

		g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
		g2.setPaint(Color.black);

		paintTree(tree, new Point2D.Double(startX, startY), g2, myFont);
	}

  public TreeJPanel() {
    this(SwingConstants.CENTER, SwingConstants.CENTER);
  }

  public TreeJPanel(int hAlign, int vAlign) {
    HORIZONTAL_ALIGN = hAlign;
    VERTICAL_ALIGN = vAlign;
    //setPreferredSize(new Dimension(preferredX, preferredY));
    Font font = getFont();
    font = new Font(font.getName(), font.getStyle(), maxFontSize);
    myFont = getFontMetrics(font);
  }

  public void setMinFontSize(int size) {
    minFontSize = size;
  }

  public void setMaxFontSize(int size) {
    maxFontSize = size;
  }

  public static void main(String[] args) throws IOException {
    TreeJPanel tjp = new TreeJPanel();
    String ptbTreeString = "(NP-2 (NP-1 (QP-1 (CD-1 One) (JJR-1 more)) (NN-2 try)) (PP-0 (IN-1 with) (NP-2 (NP-1 (NN-1 something)) (ADVP-0 (RB-1 longer)))))";//"(ROOT (S (NP (DT This)) (VP (VBZ is) (NP (DT a) (NN test))) (. .)))";
    if (args.length > 0) {
      ptbTreeString = args[0];
    }
    Tree<String> tree = (new Trees.PennTreeReader(new StringReader(ptbTreeString))).next();//new StringReader(ptbTreeString), new LabeledScoredTreeFactory(new StringLabelFactory()))).readTree();
    tjp.setTree(tree);
    JFrame frame = new JFrame();
    frame.getContentPane().add(tjp, BorderLayout.CENTER);
    frame.addWindowListener(new WindowAdapter() {
      public void windowClosing(WindowEvent e) {
        System.exit(0);
      }
    });
    frame.pack();
    //Image img = frame.createImage(frame.getWidth(),frame.getHeight());
    frame.setVisible(true);
    frame.setVisible(true);
    frame.setSize(tjp.preferredX,tjp.preferredY);
    
    int t=1;
    t++;
   
    BufferedImage bi =new BufferedImage(tjp.width(),tjp.height(),BufferedImage.TYPE_INT_ARGB);
    Graphics2D g2 = bi.createGraphics();
    //int rule = AlphaComposite.SRC_OVER;
    //AlphaComposite ac = AlphaComposite.getInstance(rule, 0f);
    //g2.setComposite(ac);
    
    
    
    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.CLEAR, 1.0f));
    Rectangle2D.Double rect = new Rectangle2D.Double(0,0,frame.getWidth(),frame.getHeight()); 
    g2.fill(rect);
    
    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    
    tjp.paintComponent(g2); //paint the graphic to the offscreen image
//    g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
    //g2.drawImage(src, null, 0, 0);
    //g2.dispose();
    
    ImageIO.write(bi,"png",new File("example.png")); //save as png format DONE!
    
    //System.exit(1);
   
  }

   

}
