"THE BERKELEY PARSER"
release 1.1
migrated from Google Code to GitHub July 2015

This package contains the Berkeley Parser as described in 

"Learning Accurate, Compact, and Interpretable Tree Annotation"
Slav Petrov, Leon Barrett, Romain Thibaux and Dan Klein 
in COLING-ACL 2006  

and

"Improved Inference for Unlexicalized Parsing"
Slav Petrov and Dan Klein 
in HLT-NAACL 2007

If you use this code in your research and would like to acknowledge it, please refer to one of those publications. Note that the jar-archive also contains all source files. For questions please contact Slav Petrov (petrov@cs.berkeley.edu). 


* PARSING
The main class of the jar-archive is the parser. By default, it will read in PTB tokenized sentences from STDIN (one per line) and write parse trees to STDOUT. It can be evoked with:

java -jar berkeleyParser.jar -gr <grammar>

The parser can produce k-best lists and parse in parallel using multiple threads. Several additional options are also available (return binarized and/or annotated trees, produce an image of the parse tree, tokenize the input, run in fast/accurate mode, print out tree likelihoods, etc.). Starting the parser without supplying a grammar file will print a list of all options. 

* ADDITIONAL TOOLS
A tool for annotating parse trees with their most likely Viterbi derivation over refined categories and scoring the subtrees can be started with

java -cp berkeleyParser.jar edu.berkeley.nlp.PCFGLA/TreeLabeler -gr <grammar>

This tool reads in parse trees from STDIN, annotates them as specified and prints them out to STDOUT. You can use
 
java -cp berkeleyParser.jar edu.berkeley.nlp.PCFGLA.TreeScorer -gr <grammar>

to compute the (log-)likelihood of a parse tree.


* GRAMMARS
Included are grammars for English, German and Chinese. For parsing English text which is not from the Wall Street Journal, we recommend that you use the English grammar after 5 split&merge iterations as experiments suggest that the 6 split&merge iterations grammars are overfitting the Wall Street Journal. Because of the coarse-to-fine method used by the parser, there is essentially no difference in parsing time between the different grammars. 


* LEARNING NEW GRAMMARS
You will need a treebank in order to learn new grammars. The package contains code for reading in some of the standard treebanks. To learn a grammar from the Wall Street Journal section of the Penn Treebank, you can execute

java -cp berkeleyParser.jar edu.berkeley.nlp.PCFGLA.GrammarTrainer -path <WSJ location> -out <grammar-file>

To learn a grammar from trees that are contained in a single file use the -treebank option, e.g.:

java -cp berkeleyParser.jar edu.berkeley.nlp.PCFGLA.GrammarTrainer -path <WSJ location> -out <grammar-file> -treebank SINGLEFILE

This will read in the WSJ training set and do 6 iterations of split, merge, smooth. An intermediate grammar file will be written to disk once in a while and you can expect the final grammar to be written to <grammar-file> after 15-20 hours. The GrammarTrainer accepts a variety of options which have been set to reasonable default values. Most of the options should be self-explaining and you are encouraged to experiment with them. Note that since EM is a local method each run will produce slightly different results. Furthermore, the default settings prune away rules with probability below a certain threshold, which greatly speeds up the training, but increases the variance. To train grammars on other training sets (e.g. for other languages), consult edu.berkeley.nlp.PCFGLA.Corpus.java and supply the correct language option to the trainer.
To the test the performance of a grammar you can use

java -cp berkeleyParser.jar edu.berkeley.nlp.PCFGLA.GrammarTester -path <WSJ location> -in <grammar-file>


* WRITING GRAMMARS TO TEXT FILES
The parser reads and writes grammar files as serialized java classes. To view the grammars, you can export them to text format with: 

java -cp berkeleyParser.jar edu/berkeley/nlp/PCFGLA/WriteGrammarToTextFile <grammar-file> <outfile>

This will create three text files. outname.grammar and outname.lexicon contain the respective rule scores and outname.words should be used with the included perl script to map words to their signatures.

* UNKNOWN WORDS
The lexicon contains arrays with scores for each (tag,signature) pair. The array entries correspond to the scores for the respective tag substates. The signatures of known words are the words themselves. Unknown words, in contrast, are classified into a set of unknown word categories. The perl script "getSignature" takes as first argument a file containing the known words (presumably produced by WriteGrammarToFile). It then reads words from STDIN and returns the word signature to STDOUT. The signatures should be used to look up the tagging probabilities of words in the lexicon file.





