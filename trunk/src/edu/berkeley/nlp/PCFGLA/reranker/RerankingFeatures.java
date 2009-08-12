/**
 * 
 */
package edu.berkeley.nlp.PCFGLA.reranker;

import edu.berkeley.nlp.PCFGLA.reranker.FeatureExtractorManager.Feature;

/**
 * Class that holds static feature classes for the reranker - allows local and non-local extractors
 * to use the same features if necessary (e.g., BigramTreeFeature).
 * 
 * @author rafferty
 *
 */
public class RerankingFeatures {

	public static class RuleFeature implements Feature {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		int parentState;
		int parentSubstate;
		int leftState;
		int leftSubstate;
		int rightState;
		int rightSubstate;
		
		/**For Serializability*/
		protected RuleFeature() { }

		public RuleFeature(int parentState, int parentSubstate, int leftState, 
				int leftSubstate, int rightState, int rightSubstate) {
			this.parentState = parentState;
			this.parentSubstate = parentSubstate;
			this.leftState = leftState;
			this.leftSubstate = leftSubstate;
			this.rightState = rightState;
			this.rightSubstate = rightSubstate;
		}

		public boolean equals(Object other) {
			if(!(other instanceof RuleFeature))
				return false;
			RuleFeature rf = (RuleFeature) other;
			if((this.parentState == rf.parentState) &&
					(this.parentSubstate == rf.parentSubstate) &&
					(this.leftState == rf.leftState) &&
					(this.leftSubstate == rf.leftSubstate) &&
					(this.rightState == rf.rightState) &&
					(this.rightSubstate == rf.rightSubstate))
				return true;
			return false;
		}
		
		public String toString() {
			return"Rule feature::" + "parentState: " + Integer.toString(this.parentState) + "; leftState: " + Integer.toString(this.leftState) + 
				"; rightState: " + Integer.toString(this.rightState);
		}
		
		public int hashCode() {
			String s = Integer.toString(this.parentState) + Integer.toString(this.parentSubstate) + Integer.toString(this.leftState) + 
			Integer.toString(this.leftSubstate) + Integer.toString(this.rightState) + Integer.toString(this.rightSubstate);
			return s.hashCode();
		}
	}

	/**
	 * Used to represent both word/preterm/parent and word/preterm/parent/grandparent features. (The
	 * former are local and the latter are non-local)
	 * @author rafferty
	 *
	 */
	public static class ThreeAncestorWord implements Feature {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		int grandParentState;
		int grandParentSubstate;
		int parentState;
		int parentSubstate;
		int preTermState;
		int preTermSubstate;
		String word;

		/**For Serializability*/
		protected ThreeAncestorWord() { }
		
		/**
		 * Non-local constructer
		 * @param parentState
		 * @param parentSubstate
		 * @param preTermState
		 * @param preTermSubstate
		 * @param word
		 */
		public ThreeAncestorWord(int grandParentState, int grandParentSubstate, int parentState, int parentSubstate, int preTermState, int preTermSubstate, String word) {
			this.grandParentState = grandParentState;
			this.grandParentSubstate = grandParentSubstate;
			this.parentState = parentState;
			this.parentSubstate = parentSubstate;
			this.preTermState = preTermState;
			this.preTermSubstate = preTermSubstate;
			this.word = word;
		}

		/**
		 * Local construct
		 * @param parentState
		 * @param parentSubstate
		 * @param preTermState
		 * @param preTermSubstate
		 * @param word
		 */
		public ThreeAncestorWord(int parentState, int parentSubstate, int preTermState, int preTermSubstate, String word) {
			this.grandParentState = -1;
			this.grandParentSubstate = -1;
			this.parentState = parentState;
			this.parentSubstate = parentSubstate;
			this.preTermState = preTermState;
			this.preTermSubstate = preTermSubstate;
			this.word = word;
		}

		public boolean equals(Object other) {
			if(!(other instanceof ThreeAncestorWord))
				return false;
			ThreeAncestorWord taw = (ThreeAncestorWord) other;
			if( (this.grandParentState == taw.grandParentState) &&
					(this.grandParentSubstate == taw.grandParentSubstate) && 
					(this.parentState == taw.parentState) &&
					(this.parentSubstate == taw.parentSubstate) && 
					(this.preTermState == taw.preTermState) && 
					(this.preTermSubstate == taw.preTermSubstate) && 
					((this.word == null && taw.word == null) || (this.word.equals(taw.word))))
				return true;
			return false;
		}
		
		public String toString() {
			return"ThreeAncestorWord:: " + "grandParentState: " + Integer.toString(this.grandParentState) + "; parentState: " + Integer.toString(this.parentState) +
			"; preTermState: " + Integer.toString(this.preTermState) + "; word: " + this.word;
		}

		public int hashCode() {
			String s = Integer.toString(this.grandParentState) + Integer.toString(this.grandParentSubstate) + Integer.toString(this.parentState) +
			Integer.toString(this.parentSubstate) + Integer.toString(this.preTermState) + Integer.toString(this.preTermSubstate) + this.word;
			return s.hashCode();
		}
	}


	public static class WordEdge implements Feature {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		int state;
		int substate;
		String leftWord;
		String rightWord;
		int numWords;

		/**For Serializability*/
		protected WordEdge() { }
		
		public WordEdge(int state, int substate, String leftWord, String rightWord, int numWords) {
			this.state = state;
			this.substate = substate;
			this.leftWord = leftWord;
			this.rightWord = rightWord;
			this.numWords = numWords;
		}

		public boolean equals(Object other) {
			if(!(other instanceof WordEdge))
				return false;
			WordEdge we = (WordEdge) other;
			if((this.state == we.state) &&
					(this.substate == we.substate) && 
					((this.leftWord == null && we.leftWord == null) || (this.leftWord.equals(we.leftWord))) && 
					((this.rightWord == null && we.rightWord == null) || (this.rightWord.equals(we.rightWord))) &&
					(this.numWords == we.numWords))
				return true;
			return false;
		}
		
		public String toString() {
			return "WordEdge:: " + "state: " + Integer.toString(this.state) + "; leftWord: " + this.leftWord + "; rightWord: " + this.rightWord + "; numWords: " + Integer.toString(this.numWords);
		}

		public int hashCode() {
			String s = Integer.toString(this.state) + Integer.toString(this.substate) + this.leftWord + this.rightWord + Integer.toString(this.numWords);
			return s.hashCode();
		}
	}


	public static class HeavyFeature implements Feature {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		int parentState;
		int parentSubstate;
		int binnedLength;
		boolean endSentence;
		boolean punctFollows;

		/**For Serializability*/
		protected HeavyFeature() { }
		
		public HeavyFeature(int parentState, int parentSubstate, int binnedLength, boolean endSentence, boolean punctFollows) {
			this.parentState = parentState;
			this.parentSubstate = parentSubstate;
			this.binnedLength = binnedLength;
			this.endSentence = endSentence;
			this.punctFollows = punctFollows;
		}

		public boolean equals(Object other) {
			if(!(other instanceof HeavyFeature))
				return false;
			HeavyFeature hf = (HeavyFeature) other;
			if((this.parentState == hf.parentState) &&
					(this.parentSubstate == hf.parentSubstate) &&
					(this.binnedLength == hf.binnedLength) &&
					(this.endSentence == hf.endSentence) &&
					(this.punctFollows == hf.punctFollows))
				return true;
			return false;
		}
		
		public String toString() {
			return "Heavy:: " + "parentState: " + Integer.toString(this.parentState) + "; binnedLength: " + Integer.toString(binnedLength) +
			"; endSentence: " + endSentence + "; punctFollows: " + punctFollows;
		}

		public int hashCode() {
			String s = Integer.toString(this.parentState) + Integer.toString(this.parentSubstate) + Integer.toString(binnedLength) +
			endSentence + punctFollows;
			return s.hashCode();
		}
	}

	public static class BigramTreeFeature implements Feature {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		int parentState;
		int parentSubstate;
		int leftState;
		int leftSubstate;
		int rightState;
		int rightSubstate;
		int leftPretermState;
		int leftPretermSubstate;
		int rightPretermState;
		int rightPretermSubstate;
		String leftWord;
		String rightWord;

		/**For Serializability*/
		protected BigramTreeFeature() { }
		
		public BigramTreeFeature(int parentState, int parentSubstate, int leftState, 
				int leftSubstate, int rightState, int rightSubstate, int leftPretermState, int leftPretermSubstate,
				int rightPretermState, int rightPretermSubstate, String leftWord, String rightWord) {
			this.parentState = parentState;
			this.parentSubstate = parentSubstate;
			this.leftState = leftState;
			this.leftSubstate = leftSubstate;
			this.rightState = rightState;
			this.rightSubstate = rightSubstate;
			this.leftPretermState = leftState;
			this.leftPretermSubstate = leftSubstate;
			this.rightPretermState = rightState;
			this.rightPretermSubstate = rightSubstate;
			this.leftWord = leftWord;
			this.rightWord = rightWord;
		}

		public boolean equals(Object other) {
			if(!(other instanceof BigramTreeFeature))
				return false;
			BigramTreeFeature btf = (BigramTreeFeature) other;
			if( (this.parentState == btf.parentState) &&
					(this.parentSubstate == btf.parentSubstate) &&
					(this.leftState == btf.leftState) &&
					(this.leftSubstate == btf.leftSubstate) &&
					(this.rightState == btf.rightState) &&
					(this.rightSubstate == btf.rightSubstate) && 
					(this.leftPretermState == btf.leftPretermState) &&
					(this.leftPretermSubstate == btf.leftPretermSubstate) &&
					(this.rightPretermState == btf.rightPretermState) &&
					(this.rightPretermSubstate == btf.rightPretermSubstate) &&
					((this.leftWord == null && btf.leftWord == null) || (this.leftWord.equals(btf.leftWord))) &&
					((this.rightWord == null && btf.rightWord == null) || (this.rightWord.equals(btf.rightWord))))
				return true;
			return false;
		}
		
		public String toString() {
			return "BigramTree:: " + "parentState: " + Integer.toString(this.parentState) + "; leftState: " + Integer.toString(this.leftState) + 
			"; rightState: " + Integer.toString(this.rightState) + "; leftPretermState: " + 
			Integer.toString(this.leftPretermState) + "; rightPretermState: " + Integer.toString(this.rightPretermState) +
			"; leftWord: " + this.leftWord + "; rightWord: " +  this.rightWord;
		}

		public int hashCode() {
			String s = Integer.toString(this.parentState) + Integer.toString(this.parentSubstate) + Integer.toString(this.leftState) + 
			Integer.toString(this.leftSubstate) + Integer.toString(this.rightState) + Integer.toString(this.rightSubstate) + 
			Integer.toString(this.leftPretermState) + Integer.toString(this.leftPretermSubstate) + Integer.toString(this.rightPretermState) +
			Integer.toString(this.rightPretermSubstate) + this.leftWord + this.rightWord;
			return s.hashCode();
		}
	}



	public static class ParentRuleFeature implements Feature {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		int grandParentState;
		int grandParentSubstate;
		int parentState;
		int parentSubstate;
		int leftState;
		int leftSubstate;
		int rightState;
		int rightSubstate;

		/**For Serializability*/
		protected ParentRuleFeature() { }

		public ParentRuleFeature(int grandParentState, int grandParentSubstate, int parentState, int parentSubstate, int leftState, 
				int leftSubstate, int rightState, int rightSubstate) {
			this.grandParentState = grandParentState;
			this.grandParentSubstate = grandParentSubstate;
			this.parentState = parentState;
			this.parentSubstate = parentSubstate;
			this.leftState = leftState;
			this.leftSubstate = leftSubstate;
			this.rightState = rightState;
			this.rightSubstate = rightSubstate;
		}

		public boolean equals(Object other) {
			if(!(other instanceof ParentRuleFeature))
				return false;
			ParentRuleFeature prf = (ParentRuleFeature) other;
			if( (this.grandParentState == prf.grandParentState) &&
					(this.grandParentSubstate == prf.grandParentSubstate) &&
					(this.parentState == prf.parentState) &&
					(this.parentSubstate == prf.parentSubstate) &&
					(this.leftState == prf.leftState) &&
					(this.leftSubstate == prf.leftSubstate) &&
					(this.rightState == prf.rightState) &&
					(this.rightSubstate == prf.rightSubstate))
				return true;
			return false;
		}

		public String toString() {
			return "ParentRule:: " + "grandParentState: " + Integer.toString(this.grandParentState) + 
			"; parentState: " + Integer.toString(this.parentState) + "; leftState: "+ Integer.toString(this.leftState) + 
			"; rightState: " + Integer.toString(this.rightState);
		}
		
		public int hashCode() {
			String s = Integer.toString(this.grandParentState) + Integer.toString(this.grandParentSubstate) + 
			Integer.toString(this.parentState) + Integer.toString(this.parentSubstate) + Integer.toString(this.leftState) + 
			Integer.toString(this.leftSubstate) + Integer.toString(this.rightState) + Integer.toString(this.rightSubstate);
			return s.hashCode();
		}
	}
	
	public static class CoarseRuleFeature implements Feature {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		int parentState;
		int leftState;
		int rightState;
		
		/**For Serializability*/
		protected CoarseRuleFeature() { }
		
		public CoarseRuleFeature(int parentState, int leftState, int rightState) {
			this.parentState = parentState;
			this.leftState = leftState;
			this.rightState = rightState;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof CoarseRuleFeature))
				return false;
			CoarseRuleFeature f = (CoarseRuleFeature)obj;
			return parentState == f.parentState &&
						 leftState == f.leftState &&
						 rightState == f.rightState;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int value = parentState;
			value = value * prime + leftState;
			value = value * prime + rightState;
			return value;
		}
		
		@Override
		public String toString() {
			return "CoarseRule:: " + "parentState: " + parentState + "; leftState: " + leftState +
			"; rightState: " + rightState;
		}
	}

	public static class EmptyNodeFeature implements Feature {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		int parentState;
		int emptyNodeState;
		
		/**For Serializability*/
		protected EmptyNodeFeature() { }
		
		public EmptyNodeFeature(int parentState, int emptyNodeState) {
			this.parentState = parentState;
			this.emptyNodeState = emptyNodeState;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof EmptyNodeFeature))
				return false;
			EmptyNodeFeature f = (EmptyNodeFeature)obj;
			return parentState == f.parentState &&
			       emptyNodeState == f.emptyNodeState;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int value = parentState;
			value = value * prime + emptyNodeState;
			return value;
		}
		
		@Override
		public String toString() {
			return "EmptyNode:: parentState: " + parentState + "; emptyNodeState: " + emptyNodeState;
		}
	}
	
	public static class AnaphorAntecedentFeature implements Feature {
		/**
		 * 
		 */
		private static final long serialVersionUID = 1L;
		int matchedState;
		int antecedentFullState;
		int anaphorFullState;
		
		/**For Serializability*/
		protected AnaphorAntecedentFeature() { }
		
		public AnaphorAntecedentFeature(int matchedState, int antecedentFullState, int anaphorFullState) {
			this.matchedState = matchedState;
			this.antecedentFullState = antecedentFullState;
			this.anaphorFullState = anaphorFullState;
		}
		
		@Override
		public boolean equals(Object obj) {
			if (!(obj instanceof AnaphorAntecedentFeature))
				return false;
			AnaphorAntecedentFeature f = (AnaphorAntecedentFeature)obj;
			return matchedState == f.matchedState &&
						 antecedentFullState == f.antecedentFullState &&
						 anaphorFullState == f.anaphorFullState;
		}
		
		@Override
		public int hashCode() {
			final int prime = 31;
			int value = matchedState;
			value = value * prime + antecedentFullState;
			value = value * prime + anaphorFullState;
			return value;
		}
		
		@Override
		public String toString() {
			return "AnaphorAntecedent:: matchedState: " + matchedState + "; antecedentFullState: " +
						 antecedentFullState + "; anaphorFullState: " + anaphorFullState;
		}
	}
}
