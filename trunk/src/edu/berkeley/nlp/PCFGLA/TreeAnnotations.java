package edu.berkeley.nlp.PCFGLA;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import edu.berkeley.nlp.ling.CollinsHeadFinder;
import edu.berkeley.nlp.syntax.Tree;
import edu.berkeley.nlp.syntax.Trees;
import edu.berkeley.nlp.util.Filter;

/**
 * Class which contains code for annotating and binarizing trees for the parser's use, and debinarizing and unannotating them for scoring.
 */
public class TreeAnnotations implements java.io.Serializable {
  private static final long serialVersionUID = 1L;
	static CollinsHeadFinder headFinder = new CollinsHeadFinder();
  /** This annotates the parse tree by adding ancestors to the tags, and then by forgetfully binarizing the tree.
   * The format goes as follows:
   *   Tag becomes Tag^Parent^Grandparent
   * Then, this is binarized, so that
   *   Tag^Parent^Grandparent produces A^Tag^Parent B... C...
   * becomes
   *   Tag^Parent^Grandparent produces A^Tag^Parent @Tag^Parent^Grandparent-&gt;_A^Tag^Parent
   *   @Tag^Parent^Grandparent-&gt;_A^Tag^Parent produces B^Tag^Parent @Tag^Parent^Grandparent-&gt;_A^Tag^Parent_B^Tag^Parent
   * and finally we trim the excess _* off to control the amount of horizontal history
   *
   * */
	 public static Tree<String> processTree(Tree<String> unAnnotatedTree,
				int nVerticalAnnotations, int nHorizontalAnnotations,
				Binarization binarization, boolean manualAnnotation) {
		 return processTree(unAnnotatedTree, nVerticalAnnotations, nHorizontalAnnotations, binarization, manualAnnotation,false, true);
	 }
  public static Tree<String> processTree(Tree<String> unAnnotatedTree,
			int nVerticalAnnotations, int nHorizontalAnnotations,
			Binarization binarization, boolean manualAnnotation,
			boolean annotateUnaryParents, boolean markGrammarSymbols) {
    Tree<String> verticallyAnnotated = unAnnotatedTree;
    if (nVerticalAnnotations==3) {
      verticallyAnnotated = annotateVerticallyTwice(unAnnotatedTree, "", "");
    } 
    else if (nVerticalAnnotations==2) {
      if (manualAnnotation) {
    	  verticallyAnnotated = annotateManuallyVertically(unAnnotatedTree, "");
      } else {
    	  verticallyAnnotated = annotateVertically(unAnnotatedTree, "");
      }
    } 
    else if (nVerticalAnnotations==1){
    	if (markGrammarSymbols) verticallyAnnotated = markGrammarNonterminals(unAnnotatedTree,"");
    	if (annotateUnaryParents) verticallyAnnotated = markUnaryParents(verticallyAnnotated);
    }
    else {
      throw new Error("the code does not exist to annotate vertically "+nVerticalAnnotations+" times");
    }
    Tree<String> binarizedTree = binarizeTree(verticallyAnnotated,binarization);
    //removeUnaryChains(binarizedTree);
    //System.out.println(binarizedTree);
    
//    if (deleteLabels) return deleteLabels(binarizedTree,true);
//    else if (deletePC) return deletePC(binarizedTree,true);
//    else 
    	return forgetLabels(binarizedTree,nHorizontalAnnotations);
    
  }

	/**
	 * Binarize a tree with the given binarization style; e.g. head binarization, left binarization, etc.
	 * 
	 * @param tree
	 * @param binarization The type of binarization used.
	 * @return
	 */
	public static Tree<String> binarizeTree(Tree<String> tree, Binarization binarization) {
		switch(binarization) {
    case LEFT:
    	return leftBinarizeTree(tree);
    case RIGHT:
    	return rightBinarizeTree(tree);
    case PARENT:
    	return parentBinarizeTree(tree);
    case HEAD:
    	return headBinarizeTree(tree);
    }
		return null;
	}

  private static Tree<String> annotateVerticallyTwice(Tree<String> tree, String parentLabel1, String parentLabel2){
    Tree<String> verticallyMarkovizatedTree;
    if (tree.isLeaf()) {
      verticallyMarkovizatedTree =  tree; //new Tree<String>(tree.getLabel());// + parentLabel);
    } else {
      List<Tree<String>> children = new ArrayList<Tree<String>>();
      for (Tree<String> child : tree.getChildren()) {
//        children.add(annotateVerticallyTwice(child, parentLabel2,"^"+tree.getLabel()));
          children.add(annotateVerticallyTwice(child, "^"+tree.getLabel(), parentLabel1));    	  
      }
   	  verticallyMarkovizatedTree = new Tree<String>(tree.getLabel() + parentLabel1+parentLabel2,children);  
    }
    return verticallyMarkovizatedTree;
  }

  private static Tree<String> annotateVertically(Tree<String> tree, String parentLabel){
    Tree<String> verticallyMarkovizatedTree;
    if (tree.isLeaf()){
      verticallyMarkovizatedTree =  tree;//new Tree<String>(tree.getLabel());// + parentLabel);
    }
    else {
      List<Tree<String>> children = new ArrayList<Tree<String>>();
      for (Tree<String> child : tree.getChildren()) {
        children.add(annotateVertically(child, "^"+tree.getLabel()));
      }
      verticallyMarkovizatedTree = new Tree<String>(tree.getLabel() + parentLabel,children);
    }
    return verticallyMarkovizatedTree;
  }

  private static Tree<String> markGrammarNonterminals(Tree<String> tree, String parentLabel){
    Tree<String> verticallyMarkovizatedTree;
    if (tree.isPreTerminal()){
      verticallyMarkovizatedTree =  tree;//new Tree<String>(tree.getLabel());// + parentLabel);
    }
    else {
      List<Tree<String>> children = new ArrayList<Tree<String>>();
      for (Tree<String> child : tree.getChildren()) {
        children.add(markGrammarNonterminals(child, "^g"));//""));//
      }
      verticallyMarkovizatedTree = new Tree<String>(tree.getLabel() + parentLabel,children);
    }
    return verticallyMarkovizatedTree;
  }
  
  private static Tree<String> markUnaryParents(Tree<String> tree){
    Tree<String> verticallyMarkovizatedTree;
    if (tree.isPreTerminal()){
      verticallyMarkovizatedTree =  tree;//new Tree<String>(tree.getLabel());// + parentLabel);
    }
    else {
      List<Tree<String>> children = new ArrayList<Tree<String>>();
      for (Tree<String> child : tree.getChildren()) {
        children.add(markUnaryParents(child));//
      }
      String add = "";
      if (!tree.getLabel().equals("ROOT"))
      	add = (children.size() == 1 ? "^u" : "");
      verticallyMarkovizatedTree = new Tree<String>(tree.getLabel() + add,children);
    }
    return verticallyMarkovizatedTree;
  }

  private static Tree<String> annotateManuallyVertically(Tree<String> tree, String parentLabel){
    Tree<String> verticallyMarkovizatedTree;
    if (tree.isPreTerminal()){
    	// split only some of the POS tags
    	// DT, RB, IN, AUX, CC, %
    	String label = tree.getLabel();
    	if (label.contains("DT") || label.contains("RB") || 
    			label.contains("IN") || label.contains("AUX") || 
    			label.contains("CC") || label.contains("%") ){
    		verticallyMarkovizatedTree = new Tree<String>(tree.getLabel() + parentLabel,tree.getChildren());
    	} else {
    		verticallyMarkovizatedTree =  tree;//new Tree<String>(tree.getLabel());// + parentLabel);
    	}
    }
    else {
      List<Tree<String>> children = new ArrayList<Tree<String>>();
      for (Tree<String> child : tree.getChildren()) {
        children.add(annotateManuallyVertically(child, "^"+tree.getLabel()));
      }
      verticallyMarkovizatedTree = new Tree<String>(tree.getLabel() + parentLabel,children);
    }
    return verticallyMarkovizatedTree;
  } 
  
  // replaces labels with three types of labels:
  // X, @X=Y and Z
  private static Tree<String> deleteLabels(Tree<String> tree, boolean isRoot) {
    String label = tree.getLabel();
    String newLabel = "";
    if (isRoot){
    	newLabel = label;
    }
    else if (tree.isPreTerminal()){
    	newLabel = "Z";
    	return new Tree<String>(newLabel,tree.getChildren());
    }
    else if (label.charAt(0)=='@') {
    	newLabel = "@X";
    }
    else newLabel = "X";

    List<Tree<String>> transformedChildren = new ArrayList<Tree<String>>();
    for (Tree<String> child : tree.getChildren()) {
      transformedChildren.add(deleteLabels(child, false));
    }
    return new Tree<String>(newLabel, transformedChildren);
  }
  
  // replaces phrasal categories with 
  // X, @X=Y but keeps POS-tags
  private static Tree<String> deletePC(Tree<String> tree, boolean isRoot) {
    String label = tree.getLabel();
    String newLabel = "";
    if (isRoot){
    	newLabel = label;
    }
    else if (tree.isPreTerminal()){
    	return tree;
    }
    else if (label.charAt(0)=='@') {
    	newLabel = "@X";
    }
    else newLabel = "X";

    List<Tree<String>> transformedChildren = new ArrayList<Tree<String>>();
    for (Tree<String> child : tree.getChildren()) {
      transformedChildren.add(deletePC(child, false));
    }
    return new Tree<String>(newLabel, transformedChildren);
  }

  
  private static Tree<String> forgetLabels(Tree<String> tree, int nHorizontalAnnotation) {
    if (nHorizontalAnnotation==-1) return tree;
  	String transformedLabel = tree.getLabel();
    if (tree.isLeaf()) {
      return new Tree<String>(transformedLabel);
    }
    //the location of the farthest _
    int firstCutIndex = transformedLabel.indexOf('_');
    int keepBeginning = firstCutIndex;
    //will become -1 when the end of the line is reached
    int secondCutIndex = transformedLabel.indexOf('_',firstCutIndex+1);
    //the location of the second farthest _
    int cutIndex = secondCutIndex;
    while (secondCutIndex != -1) {
      cutIndex = firstCutIndex;
      firstCutIndex = secondCutIndex;
      secondCutIndex = transformedLabel.indexOf('_',firstCutIndex+1);
    }
    if (nHorizontalAnnotation == 0) {
    	cutIndex = transformedLabel.indexOf('>')-1;
    	if (cutIndex > 0) transformedLabel = transformedLabel.substring(0,cutIndex);
		} else if (cutIndex > 0 && !tree.isLeaf()) {
			if (nHorizontalAnnotation == 2) {
				transformedLabel = transformedLabel.substring(0, keepBeginning) + transformedLabel.substring(cutIndex);
			} else if (nHorizontalAnnotation == 1) {
				transformedLabel = transformedLabel.substring(0, keepBeginning)	+ transformedLabel.substring(firstCutIndex);
			} else {
				throw new Error("code does not exist to horizontally annotate at level "+ nHorizontalAnnotation);
			}
		}
    List<Tree<String>> transformedChildren = new ArrayList<Tree<String>>();
    for (Tree<String> child : tree.getChildren()) {
      transformedChildren.add(forgetLabels(child,nHorizontalAnnotation));
    }
    /*if (!transformedLabel.equals("ROOT")&& transformedLabel.length()>1){
    	transformedLabel = transformedLabel.substring(0,2);
    }*/
    
    /*if (tree.isPreTerminal() && transformedLabel.length()>1){
    	if (transformedLabel.substring(0,2).equals("NN")){
    		transformedLabel = "NNX";
    	}
    	else if (transformedLabel.equals("VBZ") || transformedLabel.equals("VBP") || transformedLabel.equals("VBD") || transformedLabel.equals("VB") ){
    		transformedLabel = "VBX";
    	}
    	else if (transformedLabel.substring(0,3).equals("PRP")){
    		transformedLabel = "PRPX";
    	}
    	else if (transformedLabel.equals("JJR") || transformedLabel.equals("JJS") ){
    		transformedLabel = "JJX";
    	}
    	else if (transformedLabel.equals("RBR") || transformedLabel.equals("RBS") ){
    		transformedLabel = "RBX";
    	}
    	else if (transformedLabel.equals("WDT") || transformedLabel.equals("WP") || transformedLabel.equals("WP$")){
    		transformedLabel = "WBX";
    	}
    }*/
    return new Tree<String>(transformedLabel, transformedChildren);
  }



  static Tree<String> leftBinarizeTree(Tree<String> tree) {
    String label = tree.getLabel();
    List<Tree<String>> children = tree.getChildren();
    if (tree.isLeaf())
      return new Tree<String>(label);
    else if (children.size() == 1) {
      return new Tree<String>(label, Collections.singletonList(leftBinarizeTree(children.get(0))));
    }
    // otherwise, it's a binary-or-more local tree, so decompose it into a sequence of binary and unary trees.
    String intermediateLabel = "@"+label+"->";
    Tree<String> intermediateTree = leftBinarizeTreeHelper(tree, 0, intermediateLabel);
    return new Tree<String>(label, intermediateTree.getChildren());
  }

  private static Tree<String> leftBinarizeTreeHelper(Tree<String> tree, int numChildrenGenerated, String intermediateLabel) {
    Tree<String> leftTree = tree.getChildren().get(numChildrenGenerated);
    List<Tree<String>> children = new ArrayList<Tree<String>>(2);
    children.add(leftBinarizeTree(leftTree));
    if (numChildrenGenerated == tree.getChildren().size() - 2) {
      children.add(leftBinarizeTree(tree.getChildren().get(numChildrenGenerated+1)));
    } else if (numChildrenGenerated < tree.getChildren().size() - 2) {
      Tree<String> rightTree = leftBinarizeTreeHelper(tree, numChildrenGenerated+1, intermediateLabel+"_"+leftTree.getLabel());
      children.add(rightTree);
    }
    return new Tree<String>(intermediateLabel, children);
	}

	static Tree<String> rightBinarizeTree(Tree<String> tree) {
		String label = tree.getLabel();
		List<Tree<String>> children = tree.getChildren();
		if (tree.isLeaf())
			return new Tree<String>(label);
		else if (children.size() == 1) {
			return new Tree<String>(label, Collections
					.singletonList(rightBinarizeTree(children.get(0))));
		}
		// otherwise, it's a binary-or-more local tree, so decompose it into a
		// sequence of binary and unary trees.
		String intermediateLabel = "@" + label + "->";
		Tree<String> intermediateTree = rightBinarizeTreeHelper(tree, children
				.size() - 1, intermediateLabel);
		return new Tree<String>(label, intermediateTree.getChildren());
	}

	private static Tree<String> rightBinarizeTreeHelper(Tree<String> tree,
			int numChildrenLeft, String intermediateLabel) {
		Tree<String> rightTree = tree.getChildren().get(numChildrenLeft);
		List<Tree<String>> children = new ArrayList<Tree<String>>(2);
		if (numChildrenLeft == 1) {
			children.add(rightBinarizeTree(tree.getChildren()
					.get(numChildrenLeft - 1)));
		} else if (numChildrenLeft > 1) {
			Tree<String> leftTree = rightBinarizeTreeHelper(tree,
					numChildrenLeft - 1, intermediateLabel + "_" + rightTree.getLabel());
			children.add(leftTree);
		}
		children.add(rightBinarizeTree(rightTree));
		return new Tree<String>(intermediateLabel, children);
	}
	
	/**
	 * Binarize a tree around the head symbol. That is, when there is an n-ary
	 * rule, with n > 2, we split it into a series of binary rules with titles
	 * like [AT]JJ-R (if JJ is the head of the rule). The right part of the symbol
	 * (-R or -L) is used to indicate whether we're producing to the right or to
	 * the left of the head symbol. Thus, the head symbol is always the deepest
	 * symbol on the tree we've created.
	 * 
	 * @param tree
	 * @return
	 */
	static Tree<String> headBinarizeTree(Tree<String> tree) {
		return headParentBinarizeTree(Binarization.HEAD,tree);
	}
	
	/**
	 * Binarize a tree around the head symbol, but with symbol names derived from
	 * the parent symbol, rather than the head (as in {@link headBinarizeTree}).
	 * 
	 * @param tree
	 * @return
	 */
	static Tree<String> parentBinarizeTree(Tree<String> tree) {
		return headParentBinarizeTree(Binarization.PARENT,tree);		
	}
	
	/**
	 * Binarize a tree around its head, with the symbol names derived from either
	 * the parent or the head (as determined by binarization).
	 * <p>
	 * It calls {@link @headParentBinarizeTreeHelper} to do the messy work of
	 * binarization when that is actually necessary.
	 * 
	 * @param binarization
	 *          This determines whether the newly-created symbols are based on the
	 *          head symbol or on the parent symbol. It should be either
	 *          headBinarize or parentBinarize.
	 * @param tree
	 * @return
	 */
	private static Tree<String> headParentBinarizeTree(Binarization binarization, Tree<String> tree) {
		List<Tree<String>> children = tree.getChildren();
		if (children.size()==0) {
			return tree;
		} else if (children.size()==1) {
			List<Tree<String>> kids = new ArrayList<Tree<String>>(1);
			kids.add(headParentBinarizeTree(binarization,children.get(0)));
			return new Tree<String>(tree.getLabel(),kids);
		} else if (children.size()==2) {
			List<Tree<String>> kids = new ArrayList<Tree<String>>(2);
			kids.add(headParentBinarizeTree(binarization,children.get(0)));
			kids.add(headParentBinarizeTree(binarization,children.get(1)));
			return new Tree<String>(tree.getLabel(),kids);
		} else {
			List<Tree<String>> kids = new ArrayList<Tree<String>>(1);
			kids.add(headParentBinarizeTreeHelper(binarization, tree, 0, children.size() - 1,
					headFinder.determineHead(tree), false, ""));
			return new Tree<String>(tree.getLabel(),kids);			
		}
	}
	
	/**
	 * This binarizes a tree into a bunch of binary [at]SYM-R symbols. It assumes
	 * that this sort of binarization is always necessary, so it is only called by
	 * {@link headParentBinarizeTree}.
	 * 
	 * @param binarization
	 *          The type of new symbols to generate, either head or parent.
	 * @param tree
	 * @param leftChild
	 *          The index of the leftmost child remaining to be binarized.
	 * @param rightChild
	 *          The index of the rightmost child remaining to be binarized.
	 * @param head
	 *          The head symbol of this level of the tree.
	 * @param right
	 *          This indicates whether we have gotten to the right of the head
	 *          child yet.
	 * @return
	 */
	static Tree<String> headParentBinarizeTreeHelper(Binarization binarization,
			Tree<String> tree, int leftChild, int rightChild, Tree<String> head,
			boolean right, String productionHistory) {
		if (head==null)
			throw new Error("head is null");
		List<Tree<String>> children = tree.getChildren();
		
		//test if we've finally come to the head word
		if (!right && children.get(leftChild)==head)
			right=true;

		//prepare the parent label
		String label = null;
		if (binarization==Binarization.HEAD) {
			label = head.getLabel();
		} else if (binarization==Binarization.PARENT) {
			label = tree.getLabel();
		}
		String parentLabel = "@"+label+ (right ? "-R" : "-L")+"->"+productionHistory;
		
		//if the left child == the right child, then we only need a unary
		if (leftChild==rightChild) {
			ArrayList<Tree<String>> kids = new ArrayList<Tree<String>>(1);
			kids.add(headParentBinarizeTree(binarization, children.get(leftChild)));
			return new Tree<String>(parentLabel,kids);
		}
		
		//if we're to the left of the head word
		if (!right) {
			ArrayList<Tree<String>> kids = new ArrayList<Tree<String>>(2);
			Tree<String> child = children.get(leftChild);
			kids.add(headParentBinarizeTree(binarization, child));
			kids.add(headParentBinarizeTreeHelper(binarization, tree,leftChild+1,rightChild,head,right,productionHistory+"_"+child.getLabel()));
			return new Tree<String>(parentLabel,kids);
		}
		//if we're to the right of the head word
		else {
			ArrayList<Tree<String>> kids = new ArrayList<Tree<String>>(2);
			Tree<String> child = children.get(rightChild);
			kids.add(headParentBinarizeTreeHelper(binarization, tree,leftChild,rightChild-1,head,right,productionHistory+"_"+child.getLabel()));
			kids.add(headParentBinarizeTree(binarization, child));
			return new Tree<String>(parentLabel,kids);			
		}
	}

	public static Tree<String> unAnnotateTreeSpecial(Tree<String> annotatedTree) {
    // Remove intermediate nodes (labels beginning with "Y"
    // Remove all material on node labels which follow their base symbol (cuts at the leftmost -, ^, or : character)
    // Examples: a node with label @NP->DT_JJ will be spliced out, and a node with label NP^S will be reduced to NP
    Tree<String> debinarizedTree = Trees.spliceNodes(annotatedTree, new Filter<String>() {
      public boolean accept(String s) {
        return s.startsWith("Y");
      }
    });
    Tree<String> unAnnotatedTree = (new Trees.FunctionNodeStripper()).transformTree(debinarizedTree);
    return unAnnotatedTree;
  }

	public static Tree<String> debinarizeTree(Tree<String> annotatedTree) {
	    // Remove intermediate nodes (labels beginning with "@"
	    // Remove all material on node labels which follow their base symbol (cuts at the leftmost -, ^, or : character)
	    // Examples: a node with label @NP->DT_JJ will be spliced out, and a node with label NP^S will be reduced to NP
	    Tree<String> debinarizedTree = Trees.spliceNodes(annotatedTree, new Filter<String>() {
	      public boolean accept(String s) {
	        return s.startsWith("@") && !s.equals("@");
	      }
	    });
	    return debinarizedTree;
	}	
	
	public static Tree<String> unAnnotateTree(Tree<String> annotatedTree, boolean keepFunctionLabel) {
    // Remove intermediate nodes (labels beginning with "@"
    // Remove all material on node labels which follow their base symbol (cuts at the leftmost -, ^, or : character)
    // Examples: a node with label @NP->DT_JJ will be spliced out, and a node with label NP^S will be reduced to NP
    Tree<String> debinarizedTree = Trees.spliceNodes(annotatedTree, new Filter<String>() {
      public boolean accept(String s) {
        return s.startsWith("@") && !s.equals("@");
      }
    });
    if (keepFunctionLabel) return debinarizedTree;
    Tree<String> unAnnotatedTree = (new Trees.FunctionNodeStripper()).transformTree(debinarizedTree);
    return unAnnotatedTree;
  }
  
  public static void main(String args[]) {
  	//test the binarization
    Trees.PennTreeReader reader = new Trees.PennTreeReader(new StringReader("((S (NP (DT the) (JJ quick) (JJ (AA (BB (CC brown)))) (NN fox)) (VP (VBD jumped) (PP (IN over) (NP (DT the) (JJ lazy) (NN dog)))) (. .)))"));
    Tree<String> tree = reader.next();
    System.out.println("tree");
    System.out.println(Trees.PennTreeRenderer.render(tree));
    for (Binarization binarization : Binarization.values()) {
    	System.out.println("binarization type "+binarization.name());
    	//print the binarization
    	try {
    		Tree<String> binarizedTree = binarizeTree(tree,binarization);
    		System.out.println(Trees.PennTreeRenderer.render(binarizedTree));
    		System.out.println("unbinarized");
    		Tree<String> unBinarizedTree = unAnnotateTree(binarizedTree, false);
    		System.out.println(Trees.PennTreeRenderer.render(unBinarizedTree));
    		System.out.println("------------");
    	} catch (Error e) {
    		System.out.println("binarization not implemented");
    	}
    }
  }
  
  public static Tree<String> removeSuperfluousNodes(Tree<String> tree){
  	if (tree.isPreTerminal()) return tree;
  	if (tree.isLeaf()) return tree;
  	List<Tree<String>> gChildren = tree.getChildren();
  	if (gChildren.size()!=1) {
  		// nothing to do, just recurse
  		ArrayList<Tree<String>> children = new ArrayList<Tree<String>>();
  		for (int i=0; i<gChildren.size(); i++){
  			Tree<String> cChild = removeSuperfluousNodes(tree.getChildren().get(i));
  			children.add(cChild);
  		}
  		tree.setChildren(children);
  		return tree;
  	}
  	Tree<String> result = null;
  	String parent = tree.getLabel();
  	HashSet<String> nodesInChain = new HashSet<String>();
  	tree = tree.getChildren().get(0);
  	while (!tree.isPreTerminal() && tree.getChildren().size()==1){
  		if (!nodesInChain.contains(tree.getLabel())){
  				nodesInChain.add(tree.getLabel());
  		}
  		tree = tree.getChildren().get(0);
  	}
		Tree<String> child = removeSuperfluousNodes(tree);
		String cLabel = child.getLabel();
  	ArrayList<Tree<String>> childs = new ArrayList<Tree<String>>();
  	childs.add(child);
  	if (cLabel.equals(parent)) {
  		result = child;
  	}	else {
  		result = new Tree<String>(parent,childs);
  	}
  	for (String node : nodesInChain){
  		if (node.equals(parent)||node.equals(cLabel)) continue;
  		Tree<String> intermediate = new Tree<String>(node, result.getChildren());
  		childs = new ArrayList<Tree<String>>();
  		childs.add(intermediate);
  		result.setChildren(childs);
  	}
  	return result;
  }
  
  
    
  public static void displayUnaryChains(Tree<String> tree, String parent){
  	if (tree.getChildren().size()==1){
  		if (!parent.equals("") && !tree.isPreTerminal()) System.out.println("Unary chain: "+parent+" -> "+ tree.getLabel() + " -> " +tree.getChildren().get(0).getLabel());
  		if (!tree.isPreTerminal()) displayUnaryChains(tree.getChildren().get(0),tree.getLabel());
  	}
  	else {
  		for (Tree<String> child : tree.getChildren()){
  			if (!child.isPreTerminal()) displayUnaryChains(child,"");
  		}
  	}
  	
  }
  
 
  public static void removeUnaryChains(Tree<String> tree){
		if (tree.isPreTerminal()) return;
  	if (tree.getChildren().size()==1 && tree.getChildren().get(0).getChildren().size()==1){
  		// unary chain
  		if (tree.getChildren().get(0).isPreTerminal()) return; // if we are just above a preterminal, dont do anything
  		else {// otherwise remove the intermediate node
  			ArrayList<Tree<String>> newChildren = new ArrayList<Tree<String>>();
  			newChildren.add(tree.getChildren().get(0).getChildren().get(0));
  			tree.setChildren(newChildren);
  		}
  	}
		for (Tree<String> child : tree.getChildren()){
			removeUnaryChains(child);
		}
	}
  
}
