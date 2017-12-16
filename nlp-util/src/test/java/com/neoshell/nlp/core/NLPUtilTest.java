package com.neoshell.nlp.core;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.neoshell.nlp.core.NLPContext;
import com.neoshell.nlp.core.NLPUtil;
import com.neoshell.nlp.core.WordInfo;
import com.neoshell.nlp.test.TestUtil;

import edu.stanford.nlp.ie.crf.CRFClassifier;
import edu.stanford.nlp.ling.CoreLabel;

public class NLPUtilTest {

  private static final List<String> STOPWORDS_0 = Arrays.asList("this", "is",
      "not");
  private static final List<String> STOPWORDS_1 = Arrays.asList("这", "是", "不",
      "那");

  private static NLPUtil nlpUtil;
  private static NLPContext context;

  @BeforeClass
  public static void setUpTestData() {
    try {
      CRFClassifier<CoreLabel> segmenter = TestUtil.createSegmenter();
      nlpUtil = new NLPUtil(segmenter);
      nlpUtil.addStopwords(STOPWORDS_0);
      nlpUtil.addStopwords(STOPWORDS_1);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Before
  public void setUp() {
    Map<String, Long> globalWordCount = new HashMap<>();
    globalWordCount.put("a", 100L);
    globalWordCount.put("c", 10L);
    context = nlpUtil.generateNLPContext(globalWordCount);
  }

  @Test
  public void segment() {
    List<String> texts = Arrays.asList("这是苹果", "那是香蕉");
    List<String> expectedResult = Arrays.asList("这", "是", "苹果", "那", "是", "香蕉");
    List<String> result = nlpUtil.segment(texts);
    assertEquals(expectedResult, result);
  }

  @Test
  public void isStopWord() {
    for (String word : STOPWORDS_0) {
      assertTrue(nlpUtil.isStopWord(word));
    }
    for (String word : STOPWORDS_1) {
      assertTrue(nlpUtil.isStopWord(word));
    }
    String nonStopWord = "apple";
    assertFalse(nlpUtil.isStopWord(nonStopWord));
  }

  @Test
  public void removeStopWords() {
    List<String> words = Arrays.asList("this", "is", "apple", "not", "banana");
    List<String> expectedResult = Arrays.asList("apple", "banana");
    List<String> result = nlpUtil.removeStopWords(words);
    assertEquals(expectedResult, result);
  }

  @Test
  public void countWord() {
    List<String> texts = Arrays.asList("这是苹果", "那是香蕉", "苹果是苹果", "苹果不是香蕉");

    // Count stop words. Limit > result size.
    List<WordInfo> result = nlpUtil.countWords(texts, true, 100);
    List<WordInfo> expectedResult = Arrays.asList(
        TestUtil.createWordInfo("苹果", 4L, 0.0),
        TestUtil.createWordInfo("是", 4L, 0.0),
        TestUtil.createWordInfo("香蕉", 2L, 0.0),
        TestUtil.createWordInfo("那", 1L, 0.0),
        TestUtil.createWordInfo("这", 1L, 0.0),
        TestUtil.createWordInfo("不", 1L, 0.0));
    assertEquals(expectedResult, result);

    // Count stop words. Limit < result size.
    int limit = 4;
    result = nlpUtil.countWords(texts, true, limit);
    expectedResult = expectedResult.subList(0, limit);
    assertEquals(expectedResult, result);

    // Not count stop words. Limit > result size.
    result = nlpUtil.countWords(texts, false, 100);
    expectedResult = Arrays.asList(TestUtil.createWordInfo("苹果", 4L, 0.0),
        TestUtil.createWordInfo("香蕉", 2L, 0.0));
    assertEquals(expectedResult, result);
  }

  @Test
  public void getKeywordInfo() {
    List<String> words = Arrays.asList("a", "a", "a", "b", "b", "c");

    List<WordInfo> expectedResult0 = Arrays.asList(
        TestUtil.createWordInfo("b", 2L, 2L * Math.log((double) 110L / 2L)),
        TestUtil.createWordInfo("c", 1L, 1L * Math.log((double) 110L / 10L)),
        TestUtil.createWordInfo("a", 3L, 3L * Math.log((double) 110L / 100L)));

    // limit >= number of unique words
    List<WordInfo> result = nlpUtil.getKeywordInfo(words, context, 4);
    assertEquals(expectedResult0, result);

    // limit <= 0
    result = nlpUtil.getKeywordInfo(words, context, 0);
    assertEquals(expectedResult0, result);

    // 0 < limit < number of unique words
    result = nlpUtil.getKeywordInfo(words, context, 2);
    assertEquals(expectedResult0.subList(0, 2), result);

    // Clear frequency score.
    context = NLPContext.newBuilder().build();
    List<WordInfo> expectedResult1 = Arrays.asList(
        TestUtil.createWordInfo("a", 3L, 3.0),
        TestUtil.createWordInfo("b", 2L, 2.0),
        TestUtil.createWordInfo("c", 1L, 1.0));
    result = nlpUtil.getKeywordInfo(words, context, 0);
    assertEquals(expectedResult1, result);
  }

}
