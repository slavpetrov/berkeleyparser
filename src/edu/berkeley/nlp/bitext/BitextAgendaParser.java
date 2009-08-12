package edu.berkeley.nlp.bitext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

//import edu.berkeley.nlp.parser.BinaryRule;
//import edu.berkeley.nlp.parser.Grammar;
import edu.berkeley.nlp.bitext.GrammarStateFactory.GrammarState;
//import edu.berkeley.nlp.parser.chart.Chart;
//import edu.berkeley.nlp.parser.chart.Edge;
import edu.berkeley.nlp.util.CollectionUtils;
import edu.berkeley.nlp.util.Counter;
import fig.basic.Pair;
import edu.berkeley.nlp.util.PriorityQueue;
import fig.basic.LogInfo;

public class BitextAgendaParser implements BitextParser{

	private final BitextGrammar bitextGrammar;

	private final Grammar lGrammar, rGrammar;

	private final EdgeScorer scorer;

	private int numEdgesPopped;

	private int numEdgesDiscovered;

	private Agenda agenda;

	private BitextChart chart;

	private List<String> leftInput, rightInput;

	private boolean verbose = false;

	private boolean useOutsideEstimate = false;

	private BitextLexicon lexicon;

	private int maxNumberOfEdgesPerParse = 500000;

	private class Agenda {

		private final PriorityQueue<BitextEdge> edgePQ = new PriorityQueue<BitextEdge>();

		private final Map<BitextEdge, BitextEdge> bestEdgeMap = new HashMap<BitextEdge, BitextEdge>();

		public void add(BitextEdge edge) {
			BitextEdge curEdge = bestEdgeMap.get(edge);
			if (curEdge != null) {
				if (curEdge.getScore() >= edge.getScore())
					return;
			}
//			edgePQ.setPriority(edge, edge.getScore());
			bestEdgeMap.put(edge, edge);
		}

		public BitextEdge remove() {
//			return edgePQ.removeFirst();
			return null;
		}

		public boolean isEmpty() {
			// TODO Auto-generated method stub
			return edgePQ.isEmpty();
		}

	}

	private static class Index {

		private final GrammarState leftState, rightState;

		private final int leftIndex, rightIndex;

		public Index(final GrammarState leftState, final GrammarState rightState,
				final int leftIndex, final int rightIndex) {
			this.leftState = leftState;
			this.rightState = rightState;
			this.leftIndex = leftIndex;
			this.rightIndex = rightIndex;
		}

		@Override
		public int hashCode() {
			final int PRIME = 31;
			int result = 1;
			result = PRIME * result + leftIndex;
			result = PRIME * result + ((leftState == null) ? 0 : leftState.hashCode());
			result = PRIME * result + rightIndex;
			result = PRIME * result + ((rightState == null) ? 0 : rightState.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			final Index other = (Index) obj;
			if (leftIndex != other.leftIndex)
				return false;
			if (leftState == null) {
				if (other.leftState != null)
					return false;
			} else if (!leftState.equals(other.leftState))
				return false;
			if (rightIndex != other.rightIndex)
				return false;
			if (rightState == null) {
				if (other.rightState != null)
					return false;
			} else if (!rightState.equals(other.rightState))
				return false;
			return true;
		}

	}

	private class BitextChart {

		private final Map<BitextEdge, BitextEdge> bestEdgeMap = new HashMap<BitextEdge, BitextEdge>();

		Map<Index, List<BitextEdge>> startStartEdges = new HashMap<Index, List<BitextEdge>>();

		Map<Index, List<BitextEdge>> startStopEdges = new HashMap<Index, List<BitextEdge>>();

		Map<Index, List<BitextEdge>> stopStartEdges = new HashMap<Index, List<BitextEdge>>();

		Map<Index, List<BitextEdge>> stopStopEdges = new HashMap<Index, List<BitextEdge>>();

		Chart lChart = new Chart(lGrammar);

		Chart rChart = new Chart(rGrammar);

		public boolean contains(BitextEdge edge) {
			return bestEdgeMap.containsKey(edge);
		}

		public boolean containLeftEdge(GrammarState state, int start, int end) {
			return lChart.containsEdge(new Edge(state, start, end));
		}

		public boolean containRightEdge(GrammarState state, int start, int end) {
			return rChart.containsEdge(new Edge(state, start, end));
		}

		public boolean add(BitextEdge edge) {

			if (contains(edge)) {
				return false;
			}

			GrammarState leftState = edge.getLeftState();
			int leftStart = edge.getLeftStart();
			int leftEnd = edge.getLeftEnd();
			GrammarState rightState = edge.getRightState();
			int rightStart = edge.getRightStart();
			int rightEnd = edge.getRightEnd();

			lChart.addEdge(new Edge(leftState, leftStart, leftEnd));
			rChart.addEdge(new Edge(rightState, rightStart, rightEnd));

			Index leftLeftIndex = new Index(leftState, rightState, leftEnd, rightEnd);
			CollectionUtils.addToValueList(startStartEdges, leftLeftIndex, edge);

			Index leftRightIndex = new Index(leftState, rightState, leftEnd, rightStart);
			CollectionUtils.addToValueList(startStopEdges, leftRightIndex, edge);

			Index rightLeftIndex = new Index(leftState, rightState, leftStart, rightEnd);
			CollectionUtils.addToValueList(stopStartEdges, rightLeftIndex, edge);

			Index rightRightIndex = new Index(leftState, rightState, leftStart, rightStart);
			CollectionUtils.addToValueList(stopStopEdges, rightRightIndex, edge);

			bestEdgeMap.put(edge, edge);
			return true;
		}

		public List<BitextEdge> getStopStopEdges(GrammarState leftState, int leftEnding,
				GrammarState rightState, int rightEnding) {
			Index index = new Index(leftState, rightState, leftEnding, rightEnding);
			return CollectionUtils.getValueList(startStartEdges, index);
		}

		public List<BitextEdge> getStartStartEdges(GrammarState leftState, int leftStarting,
				GrammarState rightState, int rightStarting) {
			Index index = new Index(leftState, rightState, leftStarting, rightStarting);
			return CollectionUtils.getValueList(stopStopEdges, index);
		}

		public List<BitextEdge> getStopStartEdges(GrammarState leftState, int leftEnding,
				GrammarState rightState, int rightStarting) {
			Index index = new Index(leftState, rightState, leftEnding, rightStarting);
			return CollectionUtils.getValueList(startStopEdges, index);
		}

		public List<BitextEdge> getStartStopEdges(GrammarState leftState, int leftStarting,
				GrammarState rightState, int rightEnding) {
			Index index = new Index(leftState, rightState, leftStarting, rightEnding);
			return CollectionUtils.getValueList(stopStartEdges, index);
		}

	}

	private static enum ExtendType {
		LEFT_LEFT, LEFT_RIGHT, RIGHT_LEFT, RIGHT_RIGHT;
	}

	private void extend(BitextEdge edge, ExtendType extendType) {
		int lStart = edge.getLeftStart();
		int lEnd = edge.getLeftEnd();

		int rStart = edge.getRightStart();
		int rEnd = edge.getRightEnd();

		GrammarState lState = edge.getLeftState();
		GrammarState rState = edge.getRightState();

		List<BitextRule> binarys;
		switch (extendType) {
		case LEFT_LEFT:
			binarys = bitextGrammar.getBBRulesByLL(lState, rState);
			break;
		case LEFT_RIGHT:
			binarys = bitextGrammar.getInvertBBRulesByLR(lState, rState);
			break;
		case RIGHT_LEFT:
			binarys = bitextGrammar.getInvertBBRulesByRL(lState, rState);
			break;
		case RIGHT_RIGHT:
			binarys = bitextGrammar.getBBRulesByRR(lState, rState);
			break;
		default:
			throw new RuntimeException("Impossible");
		}

		for (BitextRule bbRule : binarys) {

			BinaryRule lbr = (BinaryRule) bbRule.getLeftRule();
			GrammarState lParentState = lbr.parent();
			BinaryRule rbr = (BinaryRule) bbRule.getRightRule();
			GrammarState rParentState = rbr.parent();

			List<BitextEdge> combiningEdges;
			switch (extendType) {
			case LEFT_LEFT:
				combiningEdges = chart.getStartStartEdges(lbr.rightChild(), lEnd, rbr
						.rightChild(), rEnd);
				break;
			case LEFT_RIGHT:
				combiningEdges = chart.getStartStopEdges(lbr.rightChild(), lEnd, rbr.leftChild(),
						rStart);
				break;
			case RIGHT_LEFT:
				combiningEdges = chart.getStopStartEdges(lbr.leftChild(), lStart, rbr
						.rightChild(), rEnd);
				break;
			case RIGHT_RIGHT:
				combiningEdges = chart.getStopStopEdges(lbr.leftChild(), lStart, rbr.leftChild(),
						rStart);
				break;
			default:
				throw new RuntimeException("Impossible");
			}

			for (BitextEdge combiningEdge : combiningEdges) {

				int newLStart = Math.min(edge.getLeftStart(), combiningEdge.getLeftStart());
				int newLEnd = Math.max(edge.getLeftEnd(), combiningEdge.getLeftEnd());

				int newRStart = Math.min(edge.getRightStart(), combiningEdge.getRightStart());
				int newREnd = Math.max(edge.getRightEnd(), combiningEdge.getRightEnd());

				BitextEdge newEdge = new BitextEdge(newLStart, newLEnd, newRStart, newREnd,
						lParentState, rParentState);
				newEdge.setInsideScore(bbRule.getScore() + edge.getInsideScore()
						+ combiningEdge.getInsideScore());
				discover(newEdge);
			}
		}
	}

	private void process(BitextEdge edge) {
		boolean added = chart.add(edge);
		if (!added)
			return;
		if (verbose)
			LogInfo.logs("Processing %s %.5f\n", edge, edge.getScore());
		numEdgesPopped++;
		int lStart = edge.getLeftStart();
		int lEnd = edge.getLeftEnd();

		int rStart = edge.getRightStart();
		int rEnd = edge.getRightEnd();

		GrammarState lState = edge.getLeftState();
		GrammarState rState = edge.getRightState();

		// UU Projection
		for (BitextRule rule : bitextGrammar.getUURulesByChildren(lState, rState)) {
			GrammarState lParent = rule.getLeftRule().parent();
			GrammarState rParent = rule.getRightRule().parent();
			BitextEdge newEdge = new BitextEdge(lStart, lEnd, rStart, rEnd, lParent, rParent);
			newEdge.setInsideScore(edge.getInsideScore() + rule.getScore());
			discover(newEdge);
		}

		// Left Term BU Projection
		for (BitextRule buRule : bitextGrammar.getLeftTermBURules(lState, rState)) {
			BinaryRule leftBR = (BinaryRule) buRule.getLeftRule();
			GrammarState tag = leftBR.leftChild();
			if (lStart > 0 && chart.containLeftEdge(tag, lStart - 1, lStart)) {
				GrammarState lParent = buRule.getLeftRule().parent();
				GrammarState rParent = buRule.getRightRule().parent();
				BitextEdge newEdge = new BitextEdge(lStart - 1, lEnd, rStart, rEnd, lParent,
						rParent);
				newEdge.setInsideScore(edge.getInsideScore() + buRule.getScore());
				discover(newEdge);
			}
		}

		// Right BU Projection
		for (BitextRule buRule : bitextGrammar.getRightTermBURules(lState, rState)) {
			BinaryRule leftBR = (BinaryRule) buRule.getLeftRule();
			GrammarState tag = leftBR.rightChild();
			if (lEnd + 1 < leftInput.size() && chart.containLeftEdge(tag, lEnd, lEnd + 1)) {
				GrammarState lParent = buRule.getLeftRule().parent();
				GrammarState rParent = buRule.getRightRule().parent();
				BitextEdge newEdge = new BitextEdge(lStart, lEnd + 1, rStart, rEnd, lParent,
						rParent);
				newEdge.setInsideScore(edge.getInsideScore() + buRule.getScore());
				discover(newEdge);
			}
		}

		// Left UB Projection
		for (BitextRule ubRule : bitextGrammar.getLeftTermUBRules(lState, rState)) {
			BinaryRule rightBR = (BinaryRule) ubRule.getRightRule();
			GrammarState tag = rightBR.leftChild();
			if (rStart > 0 && chart.containRightEdge(tag, rStart - 1, rStart)) {
				GrammarState lParent = ubRule.getLeftRule().parent();
				GrammarState rParent = ubRule.getRightRule().parent();
				BitextEdge newEdge = new BitextEdge(lStart, lEnd, rStart - 1, rEnd, lParent,
						rParent);
				newEdge.setInsideScore(edge.getInsideScore() + ubRule.getScore());
				discover(newEdge);
			}
		}

		// Right UB Projection
		for (BitextRule ubRule : bitextGrammar.getRightTermUBRules(lState, rState)) {
			BinaryRule rightBR = (BinaryRule) ubRule.getRightRule();
			GrammarState tag = rightBR.rightChild();
			if (rEnd + 1 < rightInput.size() && chart.containRightEdge(tag, rEnd, rEnd + 1)) {
				GrammarState lParent = ubRule.getLeftRule().parent();
				GrammarState rParent = ubRule.getRightRule().parent();
				BitextEdge newEdge = new BitextEdge(lStart, lEnd, rStart, rEnd + 1, lParent,
						rParent);
				newEdge.setInsideScore(edge.getInsideScore() + ubRule.getScore());
				discover(newEdge);
			}
		}

		// Binary Binary Projection
		extend(edge, ExtendType.LEFT_LEFT);
//		extend(edge, ExtendType.LEFT_RIGHT);
//		extend(edge, ExtendType.RIGHT_LEFT);
		extend(edge, ExtendType.RIGHT_RIGHT);

	}

	private void discover(BitextEdge edge) {

		if (useOutsideEstimate) {
			scorer.score(edge);
		}

		numEdgesDiscovered++;

		if (edge.getScore() == Double.NEGATIVE_INFINITY || chart.contains(edge)) {
			// The -infty comes when the outside estimate considers this edge
			// impossible.
			return;
		}
		if (verbose)
			System.err.printf("\tDiscovered %s %.5f\n", edge, edge.getScore());
		agenda.add(edge);

	}

	public int getNumEdgesPopped() {
		return numEdgesPopped;
	}

	public BitextAgendaParser(BitextGrammar bitextGrammar, BitextLexicon lexicon) {
		this.bitextGrammar = bitextGrammar;
		this.lGrammar = bitextGrammar.getLeftGrammar();
		this.rGrammar = bitextGrammar.getRightGrammar();
		this.scorer = new EdgeScorer(bitextGrammar, lexicon);
		this.lexicon = lexicon;
		this.useOutsideEstimate = true;
	}

	private void initialize(List<String> leftInput, List<String> rightInput) {
		this.chart = new BitextChart();
		this.agenda = new Agenda();
		this.leftInput = leftInput;
		this.rightInput = rightInput;
		this.numEdgesPopped = 0;
		this.numEdgesDiscovered = 0;

		lexicon.setLhsInputSentence(leftInput);
		lexicon.setRhsInputSentence(rightInput);
		this.scorer.setInput(leftInput, rightInput);

		for (int lIndex = 0; lIndex < leftInput.size(); ++lIndex) {
			for (int rIndex = 0; rIndex < rightInput.size(); ++rIndex) {
				Counter<Pair<String, String>> tagScores = lexicon.getTagScores(lIndex, rIndex);
				for (Pair<String, String> tags : tagScores.keySet()) {
					String lTag = tags.getFirst();
					String rTag = tags.getSecond();
					double score = tagScores.getCount(tags);

					GrammarState lState = bitextGrammar.getLeftGrammar().getState(lTag);
					GrammarState rState = bitextGrammar.getRightGrammar().getState(rTag);
					BitextEdge edge = new BitextEdge(lIndex, lIndex + 1, rIndex, rIndex + 1,
							lState, rState);
					edge.setInsideScore(score);
					discover(edge);
				}
			}
		}

	}

	private boolean isGoal(BitextEdge edge) {

		int lStart = edge.getLeftStart();
		int lEnd = edge.getLeftEnd();
		int rStart = edge.getRightStart();
		int rEnd = edge.getRightEnd();
		GrammarState lState = edge.getLeftState();
		GrammarState rState = edge.getRightState();

		GrammarState lGoal = bitextGrammar.getLeftGrammar().getRootState();
		GrammarState rGoal = bitextGrammar.getRightGrammar().getRootState();

		return lStart == 0 && lEnd == leftInput.size() && rStart == 0
				&& rEnd == rightInput.size() && lState.equals(lGoal) && rState.equals(rGoal);
	}

	public void parse(List<String> leftInput, List<String> rightInput, Alignment alignment) {
		try {
			initialize(leftInput, rightInput);

			BitextEdge edge = null;
			while (!agenda.isEmpty()) {
				edge = agenda.remove();
				if (isGoal(edge)) {
					break;
				}
				process(edge);
			}

			LogInfo.logsForce("Goal Edge: " + edge + " inside score " + edge.getInsideScore());
			LogInfo.logsForce("Is Goal: " + isGoal(edge));
			LogInfo.logsForce("Num Edges Processed:" + numEdgesPopped);
		} catch (RuntimeException e) {
			LogInfo.logs("Sentence raised an exception:");
			LogInfo.logs(e);
			LogInfo.logs("Skipping sentence.");
			numEdgesPopped = 0;
			numEdgesDiscovered = 0;
		}
	}

	public int getNumEdgesDiscovered() {
		return this.numEdgesDiscovered;
	}

	public void setUseOutsideEstimate(boolean useOutsideEstimate) {
		// LogInfo.logs((useOutsideEstimate ? "Using A* Estimate" : "Disabling A* Estimate"));
		this.useOutsideEstimate = useOutsideEstimate;
	}
}
