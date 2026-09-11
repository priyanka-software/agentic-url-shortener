package com.assessment.shortener.service;
import com.assessment.shortener.dto.CreateUrlRequest; import com.assessment.shortener.repository.ShortUrlRepository;
import org.junit.jupiter.api.Test; import org.junit.jupiter.api.extension.ExtendWith; import org.mockito.*; import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*; import static org.mockito.Mockito.*;
@ExtendWith(MockitoExtension.class) class UrlShortenerServiceTest {
 @Mock ShortUrlRepository repo;
 @Test void createsShortCode(){when(repo.findByShortCode(anyString())).thenReturn(java.util.Optional.empty()); UrlShortenerService s=new UrlShortenerService(repo,"http://localhost:8080"); var r=s.create(new CreateUrlRequest("https://example.com",60L)); assertEquals(7,r.shortCode().length()); verify(repo).save(any());}
 @Test void rejectsUnsafeScheme(){UrlShortenerService s=new UrlShortenerService(repo,"http://localhost:8080"); assertThrows(Exception.class,()->s.create(new CreateUrlRequest("javascript:alert(1)",null)));}
}
