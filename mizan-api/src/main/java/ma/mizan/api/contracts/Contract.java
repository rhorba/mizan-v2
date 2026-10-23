package ma.mizan.api.contracts;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** An uploaded contract: extracted text only, never the PDF bytes (ADR-5). */
@Entity
@Table(name = "contracts")
class Contract {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(name = "owner_sub", nullable = false, updatable = false, length = 64)
  private String ownerSub;

  @Column(name = "file_name", nullable = false, length = 255)
  private String fileName;

  @Column(name = "page_count", nullable = false)
  private int pageCount;

  @Column(name = "text_content", nullable = false, columnDefinition = "text")
  private String textContent;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Contract() {}

  Contract(String ownerSub, String fileName, int pageCount, String textContent) {
    this.ownerSub = ownerSub;
    this.fileName = fileName;
    this.pageCount = pageCount;
    this.textContent = textContent;
    // PostgreSQL keeps microseconds; truncate so the keyset cursor round-trips exactly.
    this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  UUID id() {
    return id;
  }

  String ownerSub() {
    return ownerSub;
  }

  String fileName() {
    return fileName;
  }

  int pageCount() {
    return pageCount;
  }

  String textContent() {
    return textContent;
  }

  Instant createdAt() {
    return createdAt;
  }
}
