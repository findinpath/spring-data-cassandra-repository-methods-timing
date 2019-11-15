package com.findinpath;

import static com.findinpath.Utils.getExactlyOneTimer;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;

import com.datastax.driver.core.utils.UUIDs;
import com.findinpath.aop.RepositoryTimerAspect;
import com.findinpath.model.Config;
import com.findinpath.model.UserBookmark;
import com.findinpath.model.UserBookmarkKey;
import com.findinpath.repository.ConfigRepository;
import com.findinpath.repository.UserBookmarkRepository;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.cassandra.core.CassandraOperations;

@SpringBootTest
public class DemoTest {

  private static Logger LOG = LoggerFactory.getLogger(DemoTest.class);

  private static final String[] BOOKMARKS = new String[]{
      "https://www.findinpath.com",
      "https://www.github.com",
      "https://www.google.com",
      "https://www.twitter.com",
      "https://www.yahoo.com",
      "https://www.topcoder.com",
      "https://www.twitter.com",
      "https://delicio.us",
      "https://www.wikipedia.com",
  };


  @Autowired
  private ConfigRepository configRepository;

  @Autowired
  private UserBookmarkRepository userBookmarkRepository;

  @Autowired
  private CassandraOperations cassandraOperations;

  @Autowired
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  @AfterEach
  public void truncateTables() {
    cassandraOperations.truncate(UserBookmark.class);
    cassandraOperations.truncate(Config.class);
  }

  /**
   * Perform several INSERT (via {@link ConfigRepository#save(Object)} method) and SELECT (via
   * {@link ConfigRepository#findById(Object)} method) Cassandra statements in a synchronous fashion
   * on the table `demo.config` and retrieve from the `meterRegistry` the `max` and `mean` times for
   * the corresponding methods from `ConfigRepository`.
   */
  @Test
  public void timingSynchronousMethodsDemo() {
    var count = 10;

    IntStream.rangeClosed(1, count).forEach(
        i -> {
          Config websiteConfig = new Config();
          var websiteKey = "website" + i;
          var websiteValue = "https://findinpath.com";
          websiteConfig.setKey(websiteKey);
          websiteConfig.setValue(websiteValue);

          configRepository.save(websiteConfig);

          var readWebsiteConfig = configRepository.findById(websiteKey);
          assertThat(readWebsiteConfig.isPresent(), equalTo(true));
          readWebsiteConfig
              .ifPresent(config -> assertThat(config.getValue(), equalTo(websiteValue)));
        }
    );

    // Now  we can inspect the timers for the ConfigRepository and see how long did the
    // INSERT and SELECT Cassandra statements take.
    var meters = meterRegistry.getMeters();
    checkTimerValidity(meters, "ConfigRepository", "save",
        Integer.toUnsignedLong(count));
    checkTimerValidity(meters, "ConfigRepository", "findById",
        Integer.toUnsignedLong(count));
  }


  /**
   * Perform several operations in synchronous and subsequently in asynchronous fashion on the table
   * `demo.user_bookmarks` (via {@link UserBookmarkRepository}) and retrieve from the
   * `meterRegistry` the max and mean times for the corresponding methods from
   * `UserBookmarkRepository`.
   *
   * This way in the log statements issued by this test case can be verified whether there is a
   * difference at the database level when doing sync vs async requests.
   */
  @Test
  public void checkSynchronousVsAsynchronousMethodTimesDemo() throws Exception {
    var syncUserId = UUID.randomUUID();
    var asyncUserId = UUID.randomUUID();
    for (int i = 0; i < BOOKMARKS.length; i++) {
      var syncUserBookmark = createUserBookmark(syncUserId, i + 1, BOOKMARKS[i]);
      userBookmarkRepository.save(syncUserBookmark);
      var asyncUserBookmark = createUserBookmark(asyncUserId, i + 1, BOOKMARKS[i]);
      userBookmarkRepository.saveAsync(asyncUserBookmark).get();

      var userBookmarksCount = i + 1;
      var syncUserBookmarks = userBookmarkRepository
          .findLatestBookmarks(syncUserId, userBookmarksCount);
      assertThat(syncUserBookmarks, hasSize(userBookmarksCount));
      // The bookmarks are ordered descending by their timestamp
      // reason why the last bookmark retrieved should be also the oldest
      assertThat(syncUserBookmarks.get(i).getUrl(), equalTo(BOOKMARKS[i]));

      var asyncUserBookmarks = userBookmarkRepository
          .findLatestBookmarksAsync(asyncUserId, userBookmarksCount).get();
      assertThat(asyncUserBookmarks, hasSize(userBookmarksCount));
      assertThat(asyncUserBookmarks.get(i).getUrl(), equalTo(BOOKMARKS[i]));

    }

    // Now  we can inspect the timers of both sync and async methods
    // from UserBookmarkRepository and see how they performed.
    var meters = meterRegistry.getMeters();
    checkTimerValidity(meters, "UserBookmarkRepository", "save",
        Integer.toUnsignedLong(BOOKMARKS.length));
    checkTimerValidity(meters, "UserBookmarkRepository", "saveAsync",
        Integer.toUnsignedLong(BOOKMARKS.length));
    checkTimerValidity(meters, "UserBookmarkRepository", "findLatestBookmarks",
        Integer.toUnsignedLong(BOOKMARKS.length));
    checkTimerValidity(meters, "UserBookmarkRepository", "findLatestBookmarksAsync",
        Integer.toUnsignedLong(BOOKMARKS.length));

  }

  private static UserBookmark createUserBookmark(UUID userId, int ageInDays, String url) {
    var userBookmark = new UserBookmark();
    userBookmark.setPrimaryKey(new UserBookmarkKey(userId,
        UUIDs.startOf(Instant.now().minus(ageInDays,
            ChronoUnit.DAYS).toEpochMilli())));
    userBookmark.setUrl(url);

    return userBookmark;
  }

  private static void checkTimerValidity(List<Meter> meters, String className, String methodName,
      long expectedCount) {
    var saveTimer = getExactlyOneTimer(meters,
        RepositoryTimerAspect.REPOSITORY_METRIC_NAME,
        Tag.of("class", className),
        Tag.of("method", methodName)
    );
    logTimerGenericInformation(saveTimer);
    assertThat(saveTimer.count(), equalTo(expectedCount));
    assertThat(saveTimer.max(TimeUnit.MILLISECONDS), greaterThan(0.0));
    assertThat(saveTimer.max(TimeUnit.MILLISECONDS),
        greaterThanOrEqualTo(saveTimer.mean(TimeUnit.MILLISECONDS)));
  }

  private static void logTimerGenericInformation(Timer timer) {
    LOG.info(
        "The timer " + timer.getId() + " has max value: " + (int) timer.max(TimeUnit.MILLISECONDS)
            + " ms and mean value: " + (int) timer.mean(TimeUnit.MILLISECONDS) + " ms");
  }
}
