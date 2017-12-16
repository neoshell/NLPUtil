package com.neoshell.nlp.messaging;

import static org.junit.Assert.*;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;

import com.neoshell.nlp.core.NLPContext;
import com.neoshell.nlp.core.NLPUtil;
import com.neoshell.nlp.core.WordInfo;
import com.neoshell.nlp.messaging.Message;
import com.neoshell.nlp.messaging.MessageAnalysisUtil;
import com.neoshell.nlp.test.TestUtil;
import com.neoshell.nlp.messaging.Conversation;

public class MessageAnalysisUtilTest {

  private static NLPUtil nlpUtil;
  private static NLPContext nlpContext;
  private static MessageAnalysisUtil messageAnalysisUtil;
  private static MessageAnalysisContext messageAnalysisUtilContext;

  // For testing private methods.
  private static Method analyzeConversationMethod;
  private static Method hasCommonKeywordMethod;
  private static Method mergeConversationMethod;
  private static Method mergeConversationsMethod;

  @BeforeClass
  public static void setUpTestData() {
    try {
      nlpUtil = new NLPUtil(TestUtil.createSegmenter());
      List<String> stopwords = Arrays.asList("this", "is", "not", "i");
      nlpUtil.addStopwords(stopwords);
      nlpContext = NLPContext.newBuilder().build();
      int timeBucketSeconds = 600; // 10 minutes.
      int commonKeywordThreshold = 1;
      int keywordLimit = 10;
      int minMessagesPerConversation = 2;
      messageAnalysisUtil = new MessageAnalysisUtil(nlpUtil);
      messageAnalysisUtilContext = MessageAnalysisContext.newBuilder()
          .setNlpContext(nlpContext).setTimeBucketSeconds(timeBucketSeconds)
          .setCommonKeywordThreshold(commonKeywordThreshold)
          .setKeywordLimit(keywordLimit)
          .setMinMessagesPerConversation(minMessagesPerConversation).build();

      analyzeConversationMethod = messageAnalysisUtil.getClass()
          .getDeclaredMethod("analyzeConversation", Conversation.class,
              NLPContext.class, int.class);
      hasCommonKeywordMethod = messageAnalysisUtil.getClass().getDeclaredMethod(
          "hasCommonKeyword", Conversation.class, Conversation.class,
          int.class);
      mergeConversationMethod = messageAnalysisUtil.getClass()
          .getDeclaredMethod("mergeConversation", Conversation.class,
              Conversation.class, int.class);
      mergeConversationsMethod = messageAnalysisUtil.getClass()
          .getDeclaredMethod("mergeConversations", LinkedList.class, int.class,
              int.class);
      analyzeConversationMethod.setAccessible(true);
      hasCommonKeywordMethod.setAccessible(true);
      mergeConversationMethod.setAccessible(true);
      mergeConversationsMethod.setAccessible(true);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  // Test private method.
  @Test
  public void analyzeConversation() {
    try {
      // Case 0: No messages.
      Conversation conversation = (Conversation) analyzeConversationMethod
          .invoke(messageAnalysisUtil, Conversation.newBuilder().build(),
              nlpContext, 0);
      assertEquals(0L, conversation.getStartTimestampSeconds());
      assertEquals(0L, conversation.getEndTimestampSeconds());
      assertEquals(0L, conversation.getNumWords());
      assertEquals(0L, conversation.getNumNonStopWords());
      assertTrue(conversation.getKeywordList().isEmpty());

      // Case 1: Has messages.
      conversation = (Conversation) analyzeConversationMethod.invoke(
          messageAnalysisUtil,
          Conversation.newBuilder()
              .addMessage(TestUtil.createMessage(0L, 600L, "user0", "user1",
                  "this is apple"))
              .addMessage(TestUtil.createMessage(1L, 1800L, "user1", "user0",
                  "I like apple"))
              .build(),
          nlpContext, 0);
      List<WordInfo> expectedKeywords = Arrays.asList(
          TestUtil.createWordInfo("apple", 2L, 2.0),
          TestUtil.createWordInfo("like", 1L, 1.0));
      assertEquals(600L, conversation.getStartTimestampSeconds());
      assertEquals(1800L, conversation.getEndTimestampSeconds());
      assertEquals(6L, conversation.getNumWords());
      assertEquals(3L, conversation.getNumNonStopWords());
      assertEquals(expectedKeywords, conversation.getKeywordList());
    } catch (Exception e) {
      e.printStackTrace();
      fail();
    }
  }

  // Test private method.
  @Test
  public void hasCommonKeyword() {
    try {
      int keywordLimit = 0;
      Message message0 = TestUtil.createMessage(0L, 600L, "user0", "user1",
          "a b c");
      Message message1 = TestUtil.createMessage(1L, 1200L, "user1", "user0",
          "a b");

      Conversation conversation0 = (Conversation) analyzeConversationMethod
          .invoke(messageAnalysisUtil,
              Conversation.newBuilder().addMessage(message0).build(),
              nlpContext, keywordLimit);
      Conversation conversation1 = (Conversation) analyzeConversationMethod
          .invoke(messageAnalysisUtil,
              Conversation.newBuilder().addMessage(message1).build(),
              nlpContext, keywordLimit);
      assertTrue((boolean) hasCommonKeywordMethod.invoke(messageAnalysisUtil,
          conversation0, conversation1, 1));
      assertTrue((boolean) hasCommonKeywordMethod.invoke(messageAnalysisUtil,
          conversation0, conversation1, 2));
      assertFalse((boolean) hasCommonKeywordMethod.invoke(messageAnalysisUtil,
          conversation0, conversation1, 3));
    } catch (Exception e) {
      e.printStackTrace();
      fail();
    }
  }

  // Test private method.
  @Test
  public void mergeConversation() {
    try {
      int keywordLimit = 0;
      Message message0 = TestUtil.createMessage(0L, 600L, "user0", "user1",
          "a b c c");
      Message message1 = TestUtil.createMessage(1L, 1200L, "user1", "user0",
          "b c d");

      Conversation conversation0 = (Conversation) analyzeConversationMethod
          .invoke(messageAnalysisUtil,
              Conversation.newBuilder().addMessage(message0).build(),
              nlpContext, keywordLimit);
      Conversation conversation1 = (Conversation) analyzeConversationMethod
          .invoke(messageAnalysisUtil,
              Conversation.newBuilder().addMessage(message1).build(),
              nlpContext, keywordLimit);
      Conversation expectedConversation = Conversation.newBuilder()
          .addMessage(message0).addMessage(message1)
          .setStartTimestampSeconds(600L).setEndTimestampSeconds(1200L)
          .setNumWords(7L).setNumNonStopWords(7L)
          .addAllKeyword(Arrays.asList(TestUtil.createWordInfo("c", 3L, 3.0),
              TestUtil.createWordInfo("b", 2L, 2.0),
              TestUtil.createWordInfo("a", 1L, 1.0),
              TestUtil.createWordInfo("d", 1L, 1.0)))
          .build();
      Conversation conversation = (Conversation) mergeConversationMethod.invoke(
          messageAnalysisUtil, conversation0, conversation1, keywordLimit);
      assertEquals(expectedConversation, conversation);
    } catch (Exception e) {
      e.printStackTrace();
      fail();
    }
  }

  // Test private method.
  @SuppressWarnings("unchecked")
  @Test
  public void mergeConversations() {
    try {
      int commonKeywordThreshold = 1;
      int keywordLimit = 0;
      Message[] messages = {
          TestUtil.createMessage(0L, 600L, "user0", "user1", "a b"),
          TestUtil.createMessage(1L, 1200L, "user1", "user0", "b c"),
          TestUtil.createMessage(2L, 1800L, "user0", "user1", "d"),
          TestUtil.createMessage(3L, 2400L, "user1", "user0", "d a"),
          TestUtil.createMessage(4L, 3000L, "user0", "user1", "c c") };
      LinkedList<Conversation> conversations = new LinkedList<>();
      for (int i = 0; i < messages.length; i++) {
        Conversation conversation = Conversation.newBuilder()
            .addMessage(messages[i]).build();
        conversation = (Conversation) analyzeConversationMethod.invoke(
            messageAnalysisUtil, conversation, nlpContext, keywordLimit);
        conversations.add(conversation);
      }
      LinkedList<Conversation> mergedConversations = null;

      // Case 0: No Conversations.
      mergedConversations = (LinkedList<Conversation>) mergeConversationsMethod
          .invoke(messageAnalysisUtil, new LinkedList<Conversation>(),
              commonKeywordThreshold, keywordLimit);
      assertTrue(mergedConversations.isEmpty());

      // Case 1: 1 Conversation.
      mergedConversations = (LinkedList<Conversation>) mergeConversationsMethod
          .invoke(messageAnalysisUtil,
              new LinkedList<Conversation>(conversations.subList(0, 1)),
              commonKeywordThreshold, keywordLimit);
      assertEquals(1, mergedConversations.size());
      assertEquals(conversations.get(0), mergedConversations.get(0));

      // Case 2: Merge multiple Conversations in single iteration.
      mergedConversations = (LinkedList<Conversation>) mergeConversationsMethod
          .invoke(messageAnalysisUtil,
              new LinkedList<Conversation>(conversations.subList(0, 3)),
              commonKeywordThreshold, keywordLimit);
      assertEquals(2, mergedConversations.size());
      assertEquals(Conversation.newBuilder().addMessage(messages[0])
          .addMessage(messages[1]).setStartTimestampSeconds(600L)
          .setEndTimestampSeconds(1200L).setNumWords(4L).setNumNonStopWords(4L)
          .addAllKeyword(Arrays.asList(TestUtil.createWordInfo("b", 2L, 2.0),
              TestUtil.createWordInfo("a", 1L, 1.0),
              TestUtil.createWordInfo("c", 1L, 1.0)))
          .build(), mergedConversations.get(0));
      assertEquals(conversations.get(2), mergedConversations.get(1));

      // Case 3: Merge multiple Conversations in multiple iterations.
      mergedConversations = (LinkedList<Conversation>) mergeConversationsMethod
          .invoke(messageAnalysisUtil,
              new LinkedList<Conversation>(conversations.subList(0, 5)),
              commonKeywordThreshold, keywordLimit);
      assertEquals(1, mergedConversations.size());
      assertEquals(Conversation.newBuilder().addMessage(messages[0])
          .addMessage(messages[1]).addMessage(messages[2])
          .addMessage(messages[3]).addMessage(messages[4])
          .setStartTimestampSeconds(600L).setEndTimestampSeconds(3000L)
          .setNumWords(9L).setNumNonStopWords(9L)
          .addAllKeyword(Arrays.asList(TestUtil.createWordInfo("c", 3L, 3.0),
              TestUtil.createWordInfo("a", 2L, 2.0),
              TestUtil.createWordInfo("b", 2L, 2.0),
              TestUtil.createWordInfo("d", 2L, 2.0)))
          .build(), mergedConversations.get(0));

    } catch (Exception e) {
      e.printStackTrace();
      fail();
    }
  }

  @Test
  public void mergeMessagesAndComputeKeywords() {
    Message message0 = TestUtil.createMessage(0L, 601L, "user0", "user1", "a");
    Message message1 = TestUtil.createMessage(1L, 902L, "user1", "user0", "b");
    Message message2 = TestUtil.createMessage(2L, 1203L, "user0", "user1", "c");
    Message message3 = TestUtil.createMessage(3L, 1504L, "user1", "user0", "a");
    Message message4 = TestUtil.createMessage(4L, 1805L, "user0", "user1", "d");
    Message message5 = TestUtil.createMessage(5L, 3006L, "user1", "user0", "e");
    Message message6 = TestUtil.createMessage(6L, 3307L, "user1", "user0", "f");

    // Case 0: No Messages.
    ArrayList<Conversation> conversations = messageAnalysisUtil
        .mergeMessagesAndComputeKeywords(new ArrayList<>(),
            messageAnalysisUtilContext);
    assertTrue(conversations.isEmpty());

    // Case 1: Has Messages but too sparse.
    conversations = messageAnalysisUtil.mergeMessagesAndComputeKeywords(
        Arrays.asList(message0, message6), messageAnalysisUtilContext);
    assertTrue(conversations.isEmpty());

    // Case 2: Has Messages.
    conversations = messageAnalysisUtil.mergeMessagesAndComputeKeywords(
        Arrays.asList(message0, message1, message2, message3, message4,
            message5, message6),
        messageAnalysisUtilContext);
    assertEquals(2, conversations.size());
    assertEquals(
        Conversation.newBuilder().addMessage(message0).addMessage(message1)
            .addMessage(message2).addMessage(message3)
            .setStartTimestampSeconds(601L).setEndTimestampSeconds(1504L)
            .setNumWords(4L).setNumNonStopWords(4L)
            .addAllKeyword(Arrays.asList(TestUtil.createWordInfo("a", 2L, 2.0),
                TestUtil.createWordInfo("b", 1L, 1.0),
                TestUtil.createWordInfo("c", 1L, 1.0)))
            .build(),
        conversations.get(0));
    assertEquals(
        Conversation.newBuilder().addMessage(message5).addMessage(message6)
            .setStartTimestampSeconds(3006L).setEndTimestampSeconds(3307L)
            .setNumWords(2L).setNumNonStopWords(2L)
            .addAllKeyword(Arrays.asList(TestUtil.createWordInfo("e", 1L, 1.0),
                TestUtil.createWordInfo("f", 1L, 1.0)))
            .build(),
        conversations.get(1));
  }

}
