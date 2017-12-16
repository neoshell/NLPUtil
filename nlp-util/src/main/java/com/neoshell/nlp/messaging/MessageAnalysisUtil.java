package com.neoshell.nlp.messaging;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.neoshell.nlp.core.NLPContext;
import com.neoshell.nlp.core.NLPUtil;
import com.neoshell.nlp.core.WordInfo;

public class MessageAnalysisUtil {

  private NLPUtil nlpUtil;

  public MessageAnalysisUtil(NLPUtil nlpUtil) {
    this.nlpUtil = nlpUtil;
  }

  private Conversation analyzeConversation(Conversation conversation,
      NLPContext nlpContext, int keywordLimit) {
    if (conversation.getMessageCount() == 0) {
      return conversation;
    }
    Conversation.Builder conversationBuilder = conversation.toBuilder();
    long startTimestamp = Long.MAX_VALUE;
    long endTimestamp = 0L;
    List<String> words = new ArrayList<>();
    for (Message message : conversation.getMessageList()) {
      startTimestamp = Math.min(startTimestamp, message.getTimestampSeconds());
      endTimestamp = Math.max(endTimestamp, message.getTimestampSeconds());
      words.addAll(nlpUtil.segment(message.getContent()));
    }
    long numWords = words.size();
    words = nlpUtil.removeStopWords(words);
    long numNonStopwords = words.size();
    List<WordInfo> keywords = nlpUtil.getKeywordInfo(words, nlpContext,
        keywordLimit);
    return conversationBuilder.setStartTimestampSeconds(startTimestamp)
        .setEndTimestampSeconds(endTimestamp).setNumWords(numWords)
        .setNumNonStopWords(numNonStopwords).addAllKeyword(keywords).build();
  }

  private boolean hasCommonKeyword(Conversation conversation0,
      Conversation conversation1, int threshold) {
    Set<String> keywordsInConversation0 = new HashSet<>();
    for (WordInfo keyword : conversation0.getKeywordList()) {
      keywordsInConversation0.add(keyword.getWord());
    }
    int count = 0;
    for (WordInfo keyword : conversation1.getKeywordList()) {
      if (keywordsInConversation0.contains(keyword.getWord())) {
        count++;
      }
      if (count >= threshold) {
        return true;
      }
    }
    return false;
  }

  // It doesn't modify the input object.
  private Conversation mergeConversation(Conversation conversation1,
      Conversation conversation2, int keywordLimit) {
    long startTimestamp = Math.min(conversation1.getStartTimestampSeconds(),
        conversation2.getStartTimestampSeconds());
    long endTimestamp = Math.max(conversation1.getEndTimestampSeconds(),
        conversation2.getEndTimestampSeconds());
    List<Message> messages = new ArrayList<>(conversation1.getMessageList());
    messages.addAll(conversation2.getMessageList());
    long numWords = conversation1.getNumWords() + conversation2.getNumWords();
    long numNonStopwords = conversation1.getNumNonStopWords()
        + conversation2.getNumNonStopWords();

    // Merge keywords.
    Map<String, WordInfo> keywordMap = new HashMap<>();
    for (WordInfo keyword1 : conversation1.getKeywordList()) {
      keywordMap.put(keyword1.getWord(), keyword1);
    }
    for (WordInfo keyword2 : conversation2.getKeywordList()) {
      String word = keyword2.getWord();
      if (keywordMap.containsKey(word)) {
        WordInfo keyword1 = keywordMap.get(word);
        long count = keyword1.getCount() + keyword2.getCount();
        double score = keyword1.getScore() + keyword2.getScore();
        keywordMap.put(word, WordInfo.newBuilder().setWord(word).setCount(count)
            .setScore(score).build());
      } else {
        keywordMap.put(word, keyword2);
      }
    }

    // Sort keywords by score in descending order.
    List<WordInfo> keywords = new ArrayList<>(keywordMap.values());
    Collections.sort(keywords, new Comparator<WordInfo>() {
      @Override
      public int compare(WordInfo o1, WordInfo o2) {
        Double score1 = o1.getScore();
        Double srore2 = o2.getScore();
        return srore2.compareTo(score1);
      }
    });
    if (keywordLimit > 0 && keywordLimit < keywords.size()) {
      keywords = keywords.subList(0, keywordLimit);
    }

    return Conversation.newBuilder().addAllMessage(messages)
        .setStartTimestampSeconds(startTimestamp)
        .setEndTimestampSeconds(endTimestamp).setNumWords(numWords)
        .setNumNonStopWords(numNonStopwords).addAllKeyword(keywords).build();
  }

  // It modifies the input object.
  private LinkedList<Conversation> mergeConversations(
      LinkedList<Conversation> conversations, int commonKeywordThreshold,
      int keywordsLimit) {
    if (conversations == null || conversations.size() < 2) {
      return conversations;
    }
    LinkedList<Conversation> newConversations = new LinkedList<>();
    boolean hasNewMergedConversation = true;
    while (hasNewMergedConversation) {
      hasNewMergedConversation = false;
      Conversation conversation1 = conversations.removeFirst();
      while (!conversations.isEmpty()) {
        Conversation conversation2 = conversations.removeFirst();
        if (hasCommonKeyword(conversation1, conversation2,
            commonKeywordThreshold)) {
          conversation1 = mergeConversation(conversation1, conversation2,
              keywordsLimit);
          hasNewMergedConversation = true;
        } else {
          newConversations.addLast(conversation1);
          conversation1 = conversation2;
        }
      }
      newConversations.addLast(conversation1);
      conversations = newConversations;
      newConversations = new LinkedList<>();
    }
    return conversations;
  }

  public ArrayList<Conversation> mergeMessagesAndComputeKeywords(
      List<Message> messages, MessageAnalysisContext context) {
    LinkedList<Conversation> conversations = new LinkedList<>();
    long currentTimeBucketIndex = -1;
    Conversation.Builder currentConversationBuilder = null;
    for (Message message : messages) {
      long timeBucketIndex = message.getTimestampSeconds()
          / context.getTimeBucketSeconds();
      if (timeBucketIndex != currentTimeBucketIndex) {
        if (currentConversationBuilder != null) {
          Conversation conversation = currentConversationBuilder.build();
          conversation = analyzeConversation(conversation,
              context.getNlpContext(), context.getKeywordLimit());
          conversations.add(conversation);
        }
        currentConversationBuilder = Conversation.newBuilder();
        currentTimeBucketIndex = timeBucketIndex;
      }
      currentConversationBuilder.addMessage(message);
    }
    // Handle the last one.
    if (currentConversationBuilder != null) {
      Conversation conversation = currentConversationBuilder.build();
      conversation = analyzeConversation(conversation, context.getNlpContext(),
          context.getKeywordLimit());
      conversations.add(conversation);
    }
    conversations = mergeConversations(conversations,
        context.getCommonKeywordThreshold(), context.getKeywordLimit());
    ArrayList<Conversation> result = new ArrayList<>();
    // Remove the Conversations with too few messages or no keyword.
    for (Conversation conversation : conversations) {
      if (conversation.getMessageCount() < context
          .getMinMessagesPerConversation()
          || conversation.getKeywordCount() == 0) {
        continue;
      }
      result.add(conversation);
    }
    return result;
  }

}
