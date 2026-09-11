package com.assessment.shortener.service;
import com.assessment.shortener.dto.*;
import com.assessment.shortener.model.ShortUrl;
import com.assessment.shortener.repository.ShortUrlRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.net.URI;
import java.time.*;
import java.security.SecureRandom;
@Service
public class UrlShortenerService {
 private static final String ALPHABET="23456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
 private final SecureRandom random=new SecureRandom(); private final ShortUrlRepository repo; private final String baseUrl;
 public UrlShortenerService(ShortUrlRepository repo,@Value("${app.base-url}") String baseUrl){this.repo=repo;this.baseUrl=baseUrl;}
 @Transactional public UrlResponse create(CreateUrlRequest req){ validate(req.url()); ShortUrl u=new ShortUrl(); u.setOriginalUrl(req.url()); u.setCreatedAt(Instant.now()); u.setExpiresAt(req.expiresInMinutes()==null?null:Instant.now().plus(Duration.ofMinutes(req.expiresInMinutes()))); u.setClickCount(0); u.setShortCode(uniqueCode()); repo.save(u); return response(u); }
 @Transactional public String resolve(String code){ ShortUrl u=get(code); if(u.getExpiresAt()!=null && Instant.now().isAfter(u.getExpiresAt())) throw new ResponseStatusException(HttpStatus.GONE,"Short URL expired"); u.setClickCount(u.getClickCount()+1); return u.getOriginalUrl(); }
 @Transactional(readOnly=true) public UrlResponse analytics(String code){return response(get(code));}
 @Transactional public void delete(String code){repo.delete(get(code));}
 private ShortUrl get(String c){return repo.findByShortCode(c).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Short URL not found"));}
 private String uniqueCode(){for(int a=0;a<5;a++){StringBuilder b=new StringBuilder();for(int i=0;i<7;i++)b.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));String c=b.toString();if(repo.findByShortCode(c).isEmpty())return c;}throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Unable to allocate short code");}
 private void validate(String raw){try{URI u=URI.create(raw);if(!("http".equalsIgnoreCase(u.getScheme())||"https".equalsIgnoreCase(u.getScheme()))||u.getHost()==null)throw new Exception();}catch(Exception e){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Only valid http/https URLs are supported");}}
 private UrlResponse response(ShortUrl u){return new UrlResponse(u.getShortCode(),baseUrl+"/"+u.getShortCode(),u.getOriginalUrl(),u.getCreatedAt(),u.getExpiresAt(),u.getClickCount());}
}
