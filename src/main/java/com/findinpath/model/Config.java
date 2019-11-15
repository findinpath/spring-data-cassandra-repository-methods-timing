package com.findinpath.model;

import java.io.Serializable;
import org.springframework.data.cassandra.core.mapping.Column;
import org.springframework.data.cassandra.core.mapping.PrimaryKey;
import org.springframework.data.cassandra.core.mapping.Table;

@Table(value = "config")
public class Config implements Serializable {

  public static final String KEY_FIELD_NAME = "key";
  public static final String VALUE_FIELD_NAME = "value";

  private static final long serialVersionUID = -5688762450158668403L;

  @PrimaryKey(KEY_FIELD_NAME)
  private String key;

  @Column(VALUE_FIELD_NAME)
  private String value;

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }
}
