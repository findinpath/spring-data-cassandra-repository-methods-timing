package com.findinpath.model;

import java.io.Serializable;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table("user_bookmarks")
public class UserBookmark implements Serializable {

  public static final String URL_FIELD_NAME = "url";

  @PrimaryKey
  private UserBookmarkKey primaryKey;

  @Column(value = URL_FIELD_NAME)
  private String url;

  public UserBookmarkKey getPrimaryKey() {
    return primaryKey;
  }

  public void setPrimaryKey(UserBookmarkKey primaryKey) {
    this.primaryKey = primaryKey;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }
}
