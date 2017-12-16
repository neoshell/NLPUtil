package com.neoshell.nlp.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import com.neoshell.nlp.core.NLPContext.Builder;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreLabel;

public class NLPUtil {

  private CRFClassifier<CoreLabel> segmenter;
  private HashSet<String> stopwords;

  public NLPUtil(CRFClassifier<CoreLabel> segmenter) {
    this.segmenter = segmenter;
    this.stopwords = new HashSet<>();
  }

  public void addStopwords(Collection<String> stopwords) {
    this.stopwords.addAll(stopwords);
  }

  public void clearStopwords() {
    stopwords.clear();
  }

  public List<String> segment(String text) {
    return segmenter.segmentString(text);
  }

  public List<String> segment(List<String> textList) {
    List<String> words = new ArrayList<>();
    for (String text : textList) {
      words.addAll(segment(text));
    }
    return words;
  }

  public boolean isStopWord(String word) {
    return stopwords.contains(word.toLowerCase());
  }

  // It doesn't modify the input list.
  public List<String> removeStopWords(List<String> words) {
    List<String> result = new ArrayList<>();
    for (String word : words) {
      if (!isStopWord(word)) {
        result.add(word);
      }
    }
    return result;
  }

  public List<WordInfo> countWords(List<String> texts, boolean countStopWords,
      int limit) {
    Map<String, Long> wordCountMap = new HashMap<>();
    List<String> words = segment(texts);
    if (!countStopWords) {
      words = removeStopWords(words);
    }
    for (String word : words) {
      if (wordCountMap.containsKey(word)) {
        wordCountMap.put(word, wordCountMap.get(word) + 1L);
      } else {
        wordCountMap.put(word, 1L);
      }
    }
    List<WordInfo> wordCountList = new ArrayList<>();
    for (Map.Entry<String, Long> mapEntry : wordCountMap.entrySet()) {
      wordCountList.add(WordInfo.newBuilder().setWord(mapEntry.getKey())
          .setCount(mapEntry.getValue()).build());
    }
    Collections.sort(wordCountList, new Comparator<WordInfo>() {
      @Override
      public int compare(WordInfo arg0, WordInfo arg1) {
        return -Long.compare(arg0.getCount(), arg1.getCount());
      }
    });
    if (limit < wordCountList.size()) {
      return wordCountList.subList(0, limit);
    }
    return wordCountList;
  }

  public List<WordInfo> getKeywordInfo(List<String> words, NLPContext context,
      int limit) {
    Map<String, Long> localWordCount = countWords(words);
    List<WordInfo> wordInfoList = new ArrayList<>();
    long numAllWords = context.getNumAllWords();
    Map<String, WordInfo> globalWordStats = context.getGlobalWordStatsMap();
    for (Map.Entry<String, Long> wordCount : localWordCount.entrySet()) {
      String word = wordCount.getKey();
      long count = wordCount.getValue();
      double score = count;
      if (numAllWords > 0) {
        double frequencyScore = globalWordStats.containsKey(word)
            ? globalWordStats.get(word).getScore()
            : Math.log((double) numAllWords / count);
        score = count * frequencyScore;
      }
      wordInfoList.add(WordInfo.newBuilder().setWord(word).setCount(count)
          .setScore(score).build());
    }

    // Sort by score in descending order.
    Collections.sort(wordInfoList, new Comparator<WordInfo>() {
      @Override
      public int compare(WordInfo o1, WordInfo o2) {
        Double score1 = o1.getScore();
        Double srore2 = o2.getScore();
        return srore2.compareTo(score1);
      }
    });
    if (limit > 0 && limit < wordInfoList.size()) {
      return wordInfoList.subList(0, limit);
    }
    return wordInfoList;
  }

  public NLPContext generateNLPContext(Map<String, Long> globalWordCount) {
    Builder nlpContextBuilder = NLPContext.newBuilder();
    long numAllWords = 0L;
    for (long count : globalWordCount.values()) {
      numAllWords += count;
    }
    for (Map.Entry<String, Long> wordCount : globalWordCount.entrySet()) {
      String word = wordCount.getKey();
      long count = wordCount.getValue();
      double frequencyScore = Math.log((double) numAllWords / count);
      nlpContextBuilder.putGlobalWordStats(word, WordInfo.newBuilder()
          .setWord(word).setCount(count).setScore(frequencyScore).build());
    }
    return nlpContextBuilder.setNumAllWords(numAllWords).build();
  }

  private Map<String, Long> countWords(List<String> words) {
    Map<String, Long> count = new HashMap<>();
    for (String word : words) {
      if (!count.containsKey(word)) {
        count.put(word, 1L);
      } else {
        count.put(word, count.get(word) + 1L);
      }
    }
    return count;
  }

}
