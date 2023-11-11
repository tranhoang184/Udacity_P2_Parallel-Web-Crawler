package com.udacity.webcrawler;

import com.udacity.webcrawler.json.CrawlResult;

import javax.inject.Inject;
import javax.inject.Provider;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import com.udacity.webcrawler.parser.PageParser;
import com.udacity.webcrawler.parser.PageParserFactory;

/**
 * A concrete implementation of {@link WebCrawler} that runs multiple threads on a
 * {@link ForkJoinPool} to fetch and process multiple web pages in parallel.
 */
final class ParallelWebCrawler implements WebCrawler {
  private final Clock clock;
  private final Duration timeout;
  private final int popularWordCount;
  private final ForkJoinPool pool;
  private final int maxDepth;
  private final PageParserFactory parserFactory;
  private final List<Pattern> ignoredUrls;
  @Inject
  ParallelWebCrawler(
      Clock clock,
      PageParserFactory parserFactory,
      @Timeout Duration timeout,
      @PopularWordCount int popularWordCount,
      @TargetParallelism int threadCount,
      @IgnoredUrls List<Pattern> ignoredUrls,
      @MaxDepth int maxDepth ) {
    this.clock = clock;
    this.parserFactory = parserFactory;
    this.timeout = timeout;
    this.popularWordCount = popularWordCount;
    this.pool = new ForkJoinPool(Math.min(threadCount, getMaxParallelism()));
    this.ignoredUrls = ignoredUrls;
    this.maxDepth = maxDepth;
  }

  @Override
  public CrawlResult crawl(List<String> startUrls) {
    Instant deadline = clock.instant().plus(timeout);
    ConcurrentHashMap<String, Integer> counts = new ConcurrentHashMap<>();
    ConcurrentSkipListSet<String> visitedUrls = new ConcurrentSkipListSet<>();
    startUrls.stream().forEach(url -> {pool.invoke(new CrawParallel(url, deadline, counts, visitedUrls, maxDepth));});
    return new CrawlResult.Builder()
            .setWordCounts(WordCounts.sort(counts, popularWordCount))
            .setUrlsVisited(visitedUrls.size())
            .build();
  }
  private class CrawParallel extends RecursiveTask {
    List<CrawParallel> tasks = new ArrayList<>();
    final String url;
    Instant deadline;
    int maxDepth;
    Map<String, Integer> counts;
    Set<String> visitedUrls;
    CrawParallel(String url, Instant deadline, Map<String, Integer> counts, Set<String> visitedUrls, int maxDepth){
      this.url = url;
      this.deadline = deadline;
      this.counts = counts;
      this.visitedUrls = visitedUrls;
      this.maxDepth = maxDepth;
    }
    @Override
    protected Set<String> compute(){
      if (maxDepth == 0 || clock.instant().isAfter(deadline)) {
        return new HashSet<String>();
      }
      for (Pattern pattern : ignoredUrls) {
        if (pattern.matcher(url).matches()) {
          return new HashSet<String>();
        }
      }
      if (!visitedUrls.add(url)) {
        return new HashSet<String>();
      }
      PageParser.Result result = parserFactory.get(url).parse();
      updateWordCounts(result.getWordCounts(), counts);
      for (String links : result.getLinks()) {
        tasks.add(new CrawParallel(links, deadline, counts,visitedUrls,maxDepth - 1));
      }
      invokeAll(tasks);
      return visitedUrls;
    }
  }
  private void updateWordCounts(Map<String, Integer> wordCounts, Map<String, Integer> counts) {
    for (Map.Entry<String, Integer> entry : wordCounts.entrySet()) {
      counts.compute(entry.getKey(), (key, value) -> (value == null) ? entry.getValue() : entry.getValue() + value);
    }
  }
  @Override
  public int getMaxParallelism() {
    return Runtime.getRuntime().availableProcessors();
  }
}
