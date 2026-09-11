package com.assessment.shortener.model;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name="short_urls", indexes=@Index(name="idx_short_code", columnList="shortCode", unique=true))
public class ShortUrl {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false, unique=true, length=16) private String shortCode;
 @Column(nullable=false, length=2048) private String originalUrl;
 @Column(nullable=false) private Instant createdAt;
 private Instant expiresAt;
 @Column(nullable=false) private long clickCount;
 public Long getId(){return id;} public String getShortCode(){return shortCode;} public void setShortCode(String v){shortCode=v;}
 public String getOriginalUrl(){return originalUrl;} public void setOriginalUrl(String v){originalUrl=v;}
 public Instant getCreatedAt(){return createdAt;} public void setCreatedAt(Instant v){createdAt=v;}
 public Instant getExpiresAt(){return expiresAt;} public void setExpiresAt(Instant v){expiresAt=v;}
 public long getClickCount(){return clickCount;} public void setClickCount(long v){clickCount=v;}
}
