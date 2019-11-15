package com.findinpath.model;

import com.datastax.driver.core.DataType;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.cassandra.core.cql.Ordering;
import org.springframework.data.cassandra.core.cql.PrimaryKeyType;
import org.springframework.data.cassandra.core.mapping.CassandraType;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyClass;
import org.springframework.data.cassandra.core.mapping.PrimaryKeyColumn;

@PrimaryKeyClass
public class UserBookmarkKey implements Serializable {

  public static final String USER_ID_FIELD_NAME = "user_id";
  public static final String TIMESTAMP_FIELD_NAME = "timestamp";

  @PrimaryKeyColumn(name = USER_ID_FIELD_NAME, ordinal = 0, type = PrimaryKeyType.PARTITIONED)
  @CassandraType(type = DataType.Name.UUID)
  private UUID userId;

  @PrimaryKeyColumn(name = TIMESTAMP_FIELD_NAME, ordinal = 1, type = PrimaryKeyType.CLUSTERED, ordering = Ordering.DESCENDING)
  private UUID timestamp;

  public UserBookmarkKey(UUID userId, UUID timestamp) {
    this.userId = userId;
    this.timestamp = timestamp;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public UUID getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(UUID timestamp) {
    this.timestamp = timestamp;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    UserBookmarkKey that = (UserBookmarkKey) o;
    return Objects.equals(userId, that.userId) &&
        Objects.equals(timestamp, that.timestamp);
  }

  @Override
  public int hashCode() {
    return Objects.hash(userId, timestamp);
  }
}
