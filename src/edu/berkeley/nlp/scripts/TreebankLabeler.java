///**
// * 
// */
//package edu.berkeley.nlp.scripts;
//
///**
// * @author petrov
// *
// */
//public class TreebankLabeler {
//
//	public static List getTrees(String path, int low, int high, int minLength, int maxLength) {
//    Treebank treebank = new DiskTreebank(new TreeReaderFactory() {
//      public TreeReader newTreeReader(Reader in) {
//        return new PennTreeReader(in, new LabeledScoredTreeFactory(new WordFactory()), new BobChrisTreeNormalizer());
//      }
//    });
//    treebank.loadPath(path, new NumberRangeFileFilter(low, high, true));
//    List trees = new ArrayList();
//    for (Iterator treeI = treebank.iterator(); treeI.hasNext();) {
//      Tree tree = (Tree) treeI.next();
//      if (tree.yield().size() <= maxLength && tree.yield().size() >= minLength) {
//        trees.add(tree);
//      }
//    }
//    return trees;
//  }
//
//  public static void main(String[] args) {
//    String path = args[0];
//    List trees = getTrees(path, 200, 219, 0, 10);
//    ((Tree) trees.iterator().next()).pennPrint();
//    Options op = new Options();
//    List annotatedTrees = TreebankAnnotator.removeDependencyRoots(new TreebankAnnotator(op, path).annotateTrees(trees));
//    ((Tree) annotatedTrees.iterator().next()).pennPrint();
//  }
//
// }
