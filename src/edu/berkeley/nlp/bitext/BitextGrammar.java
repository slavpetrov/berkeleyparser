package edu.berkeley.nlp.bitext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//import edu.berkeley.nlp.parser.BinaryRule;
//import edu.berkeley.nlp.parser.Grammar;
//import edu.berkeley.nlp.parser.UnaryRule;
import edu.berkeley.nlp.bitext.GrammarStateFactory.GrammarState;
import edu.berkeley.nlp.util.CollectionUtils;
import fig.basic.Indexer;
import fig.basic.Pair;
import fig.basic.LogInfo;

public class BitextGrammar {

	// BU - Binary Unary 
	Map<Pair<GrammarState, GrammarState>, List<BitextRule>>  leftBURules = new HashMap<Pair<GrammarState,GrammarState>, List<BitextRule>>();
	Map<Pair<GrammarState, GrammarState>, List<BitextRule>>  rightBURules = new HashMap<Pair<GrammarState,GrammarState>, List<BitextRule>>();
	
	// UB - Unary Binary
	Map<Pair<GrammarState, GrammarState>, List<BitextRule>>  leftUBRules = new HashMap<Pair<GrammarState,GrammarState>, List<BitextRule>>();
	Map<Pair<GrammarState, GrammarState>, List<BitextRule>>  rightUBRules = new HashMap<Pair<GrammarState,GrammarState>, List<BitextRule>>();
	
	// UU - Unary Unary
	Map<Pair<GrammarState, GrammarState>, List<BitextRule>>  uuRulesByC = new HashMap<Pair<GrammarState,GrammarState>, List<BitextRule>>();
	Map<Pair<GrammarState, GrammarState>, List<BitextRule>>  uuRulesByP = new HashMap<Pair<GrammarState,GrammarState>, List<BitextRule>>();
	
	// BB - Binary Binary
	Map<Pair<GrammarState, GrammarState>, List<BitextRule>>  bbRulesByLL = new HashMap<Pair<GrammarState,GrammarState>, List<BitextRule>>();
	Map<Pair<GrammarState, GrammarState>, List<BitextRule>>  bbRulesByRR = new HashMap<Pair<GrammarState,GrammarState>, List<BitextRule>>();
	Map<Pair<GrammarState, GrammarState>, List<BitextRule>>  invertBBRulesByLR = new HashMap<Pair<GrammarState,GrammarState>, List<BitextRule>>();
	Map<Pair<GrammarState, GrammarState>, List<BitextRule>>  invertBBRulesByRL = new HashMap<Pair<GrammarState,GrammarState>, List<BitextRule>>();
	Map<Pair<GrammarState, GrammarState>, List<BitextRule>>  bbRulesByP = new HashMap<Pair<GrammarState,GrammarState>, List<BitextRule>>();
	Map<Pair<GrammarState, GrammarState>, List<BitextRule>>  invertBBRulesByP = new HashMap<Pair<GrammarState,GrammarState>, List<BitextRule>>();
	
	Indexer<String> bitextStateIndexer;
	
	public BitextGrammar(Grammar leftGrammar, Grammar rightGrammar) {
		this.leftGrammar  = leftGrammar;
		this.rightGrammar = rightGrammar;
		LogInfo.logsForce("Left grammar symbols: "+leftGrammar.getNumStates());
		LogInfo.logsForce("Right grammar symbols: "+rightGrammar.getNumStates());
		bitextStateIndexer = new Indexer<String>();
	}
	
	public List<BitextRule> getLeftTermBURules(GrammarState lState, GrammarState rState) {
		return getRules(leftBURules, lState, rState);
	}
	
	public List<BitextRule> getRightTermBURules(GrammarState lState, GrammarState rState) {
		return getRules(rightBURules, lState, rState);
	}
	
	public List<BitextRule> getLeftTermUBRules(GrammarState lState, GrammarState rState) {
		return getRules(leftUBRules, lState, rState);
	}
	
	public List<BitextRule> getRightTermUBRules(GrammarState lState, GrammarState rState) {
		return getRules(rightUBRules, lState, rState);
	}
	
	public List<BitextRule> getUURulesByChildren(GrammarState lState, GrammarState rState) {
		return getRules(uuRulesByC, lState, rState);
	}
	
	public List<BitextRule> getUURulesByParent(GrammarState lState, GrammarState rState) {
		return getRules(uuRulesByP, lState, rState);
	}

	public List<BitextRule> getInvertBBRulesByLR(GrammarState lState, GrammarState rState) {
		return getRules(invertBBRulesByLR, lState, rState);
	}
	
	public List<BitextRule> getInvertBBRulesByRL(GrammarState lState, GrammarState rState) {
		return getRules(invertBBRulesByRL, lState, rState);
	}
	
	public List<BitextRule> getInvertBBRulesByP(GrammarState lState, GrammarState rState) {
		return getRules(invertBBRulesByP, lState, rState);
	}

	public List<BitextRule> getBBRulesByLL(GrammarState lState, GrammarState rState) {
		return getRules(bbRulesByLL, lState, rState);
	}
	
	public List<BitextRule> getBBRulesByRR(GrammarState lState, GrammarState rState) {
		return getRules(bbRulesByRR, lState, rState);
	}
	
	public List<BitextRule> getBBRulesByP(GrammarState lState, GrammarState rState) {
		return getRules(bbRulesByP, lState, rState);
	}

	public int getBitextStateIndex(String tagpair){
		return bitextStateIndexer.indexOf(tagpair);
	}
	
	public int getBitextStateIndex(String lTag, String rTag){
		return bitextStateIndexer.indexOf(lTag+"+"+rTag);
	}
	
	public int getBitextStateIndex(GrammarState lState, GrammarState rState){
		return bitextStateIndexer.indexOf(lState.label()+"+"+rState.label());
	}
	public int getNumberStatePairs(){
		return bitextStateIndexer.size();
	}

	
	Grammar leftGrammar, rightGrammar;
	Collection<BitextRule> rules = new ArrayList<BitextRule>();
	
	public void addRule(BitextRule bitextRule) {
		indexRuleStates(bitextRule);
		
		rules.add(bitextRule);
		
		switch (bitextRule.getType()) {
		case UNARY_UNARY:
			UnaryRule leftUR = (UnaryRule) bitextRule.getLeftRule();
			UnaryRule rightUR = (UnaryRule) bitextRule.getRightRule();
			Pair<GrammarState, GrammarState> pair = new Pair<GrammarState, GrammarState>(leftUR.child(), rightUR.child());
			CollectionUtils.addToValueList(uuRulesByC, pair, bitextRule);
			pair = new Pair<GrammarState, GrammarState>(leftUR.parent(), rightUR.parent());
			CollectionUtils.addToValueList(uuRulesByP, pair, bitextRule);
			break;
		case BINARY_UNARY:
			BinaryRule leftBR = (BinaryRule) bitextRule.getLeftRule();
			rightUR = (UnaryRule) bitextRule.getRightRule();			
			if (leftGrammar.isTag( leftBR.leftChild() )) {
				pair = new Pair<GrammarState, GrammarState>(leftBR.rightChild(), rightUR.child());				
				CollectionUtils.addToValueList(leftBURules, pair, bitextRule);
			} else {
				pair = new Pair<GrammarState, GrammarState>(leftBR.leftChild(), rightUR.child());
				CollectionUtils.addToValueList(rightBURules, pair, bitextRule);
			}			
			break;
		case UNARY_BINARY:
			leftUR = (UnaryRule) bitextRule.getLeftRule();
			BinaryRule rightBR = (BinaryRule) bitextRule.getRightRule(); 
			if (rightGrammar.isTag( rightBR.leftChild() )) {
				pair = new Pair<GrammarState, GrammarState>(leftUR.child(), rightBR.rightChild());
				CollectionUtils.addToValueList(leftUBRules, pair, bitextRule);
			} else {
				pair = new Pair<GrammarState, GrammarState>(leftUR.child(), rightBR.leftChild());
				CollectionUtils.addToValueList(rightUBRules, pair, bitextRule);
			}
			break;
		case BINARY_BINARY:
			leftBR = (BinaryRule) bitextRule.getLeftRule();
			rightBR = (BinaryRule) bitextRule.getRightRule();
						
			if (bitextRule.isInverted()) {
				//	lr
				pair = new Pair<GrammarState, GrammarState>(leftBR.leftChild(), rightBR.rightChild());
				CollectionUtils.addToValueList(invertBBRulesByLR, pair, bitextRule);
				// rl
				pair = new Pair<GrammarState, GrammarState>(leftBR.rightChild(), rightBR.leftChild());
				CollectionUtils.addToValueList(invertBBRulesByRL, pair, bitextRule);
				// p
				pair = new Pair<GrammarState, GrammarState>(leftBR.parent(), rightBR.parent());
				CollectionUtils.addToValueList(invertBBRulesByP, pair, bitextRule);
			} else {
				// ll
				pair = new Pair<GrammarState, GrammarState>(leftBR.leftChild(), rightBR.leftChild());
				CollectionUtils.addToValueList(bbRulesByLL, pair, bitextRule);
				// rr
				pair = new Pair<GrammarState, GrammarState>(leftBR.rightChild(), rightBR.rightChild());
				CollectionUtils.addToValueList(bbRulesByRR, pair, bitextRule);
				// p
				pair = new Pair<GrammarState, GrammarState>(leftBR.parent(), rightBR.parent());
				CollectionUtils.addToValueList(bbRulesByP, pair, bitextRule);
			}
			break;
		default:
			throw new RuntimeException("Something wrong..");
		}
		
	}

	/**
	 * @param bitextRule
	 */
	public void indexRuleStates(BitextRule bitextRule) {
		Rule lRule = bitextRule.getLeftRule();
		Rule rRule = bitextRule.getRightRule();
		bitextStateIndexer.add(lRule.parent.label()+"+"+rRule.parent.label());
		bitextRule.parentIndex = getBitextStateIndex(lRule.parent, rRule.parent);

		switch(bitextRule.getType()){
			case UNARY_UNARY:
				String s = ((UnaryRule)lRule).child().label()+"+"+((UnaryRule)rRule).child().label();
				bitextStateIndexer.add(s);
				bitextRule.childIndex = getBitextStateIndex(s);
				break;
			case BINARY_UNARY:
				BinaryRule lbR = (BinaryRule)lRule;
				UnaryRule ruR = (UnaryRule)rRule;
				if (leftGrammar.isTag( lbR.leftChild() )) {
					String s2 = lbR.rightChild().label()+"+"+ruR.child().label();
					bitextStateIndexer.add(s2);
					bitextRule.rChildIndex = getBitextStateIndex(s2);
				} else {
					String s1 = lbR.leftChild().label()+"+"+ruR.child().label();
					bitextStateIndexer.add(s1);
					bitextRule.lChildIndex = getBitextStateIndex(s1);
				}
				break;
			case UNARY_BINARY:
				UnaryRule luR = (UnaryRule)lRule;
				BinaryRule rbR = (BinaryRule)rRule;
				if (rightGrammar.isTag( rbR.leftChild() )) {
					String s4 = luR.child().label()+"+"+rbR.rightChild().label();
					bitextStateIndexer.add(s4);
					bitextRule.rChildIndex = getBitextStateIndex(s4);
				} else {
					String s3 = luR.child().label()+"+"+rbR.leftChild().label();
					bitextStateIndexer.add(s3);
					bitextRule.lChildIndex = getBitextStateIndex(s3);
				}
				break;
			case BINARY_BINARY:
				BinaryRule lR = (BinaryRule)lRule;
				BinaryRule rR = (BinaryRule)rRule;
				if (bitextRule.isInverted()){
					String s5 = lR.leftChild().label()+"+"+rR.rightChild().label();
					String s6 = lR.rightChild().label()+"+"+rR.leftChild().label();
					bitextStateIndexer.add(s5);
					bitextStateIndexer.add(s6);
					bitextRule.lChildIndex = getBitextStateIndex(s5);
					bitextRule.rChildIndex = getBitextStateIndex(s6);
				} else {
					String s7 = lR.leftChild().label()+"+"+rR.leftChild().label();
					String s8 = lR.rightChild().label()+"+"+rR.rightChild().label();
					bitextStateIndexer.add(s7);
					bitextStateIndexer.add(s8);
					bitextRule.lChildIndex = getBitextStateIndex(s7);
					bitextRule.rChildIndex = getBitextStateIndex(s8);
					
				}
				
				break;
		}
	}

	public Collection<BitextRule> getRules() {
		return rules;
	}

	public Grammar getLeftGrammar() {
		return leftGrammar;		
	}
	
	public Grammar getRightGrammar() {
		return rightGrammar;
	}
		
	private static Pair<GrammarState, GrammarState> queryPair = new Pair<GrammarState, GrammarState>(null, null);
	
	private List<BitextRule> getRules(Map<Pair<GrammarState, GrammarState>, List<BitextRule>> ruleMap, GrammarState lState, GrammarState rState) {
		queryPair.setFirst(lState);
		queryPair.setSecond(rState);
		return CollectionUtils.getValueList(ruleMap, queryPair);
	}
	
	public String toString() {
		return rules.toString();
	}
}
