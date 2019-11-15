package com.findinpath.repository;

import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;

import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.querybuilder.Select;
import com.findinpath.model.UserBookmark;
import com.findinpath.model.UserBookmarkKey;
import java.util.List;
import java.util.UUID;
import org.springframework.data.cassandra.core.AsyncCassandraOperations;
import org.springframework.data.cassandra.core.CassandraOperations;
import org.springframework.stereotype.Repository;
import org.springframework.util.concurrent.ListenableFuture;

@Repository
public class UserBookmarkRepository {

  private final CassandraOperations cassandraOperations;
  private final AsyncCassandraOperations asyncCassandraOperations;
  private final String tableName;

  public UserBookmarkRepository(
      CassandraOperations cassandraOperations,
      AsyncCassandraOperations asyncCassandraOperations) {

    this.cassandraOperations = cassandraOperations;
    this.asyncCassandraOperations = asyncCassandraOperations;
    this.tableName = cassandraOperations.getTableName(UserBookmark.class).toCql();
  }

  public List<UserBookmark> findLatestBookmarks(UUID userId, int limit) {
    Select select = QueryBuilder.select().from(tableName);

    select.where(eq(UserBookmarkKey.USER_ID_FIELD_NAME, userId))
        .limit(limit);

    return cassandraOperations.select(select, UserBookmark.class);
  }

  public ListenableFuture<List<UserBookmark>> findLatestBookmarksAsync(UUID userId, int limit) {
    Select select = QueryBuilder.select().from(tableName);

    select.where(eq(UserBookmarkKey.USER_ID_FIELD_NAME, userId))
        .limit(limit);

    return asyncCassandraOperations.select(select, UserBookmark.class);
  }

  public UserBookmark save(UserBookmark userBookmark) {
    return cassandraOperations.insert(userBookmark);
  }

  public ListenableFuture<UserBookmark> saveAsync(UserBookmark userBookmark) {
    return asyncCassandraOperations.insert(userBookmark);
  }

}
