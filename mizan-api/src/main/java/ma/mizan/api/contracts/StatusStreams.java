package ma.mizan.api.contracts;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Story 3.4 / ADR-7: the SSE streams this API instance holds, by contract. Every instance consumes
 * the status topic with its own consumer group (random id), so a change made on one instance
 * reaches browsers connected to any of them.
 */
@Component
class StatusStreams {

  private static final Logger LOG = LoggerFactory.getLogger(StatusStreams.class);

  /** Browsers reconnect or fall back to polling after this. */
  static final Duration TIMEOUT = Duration.ofMinutes(5);

  private final Map<UUID, Set<SseEmitter>> byContract = new ConcurrentHashMap<>();

  /** Opens a stream and sends the current status first, so the page cannot miss a change. */
  SseEmitter open(UUID contractId, ContractController.AnalysisView current) {
    SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
    Set<SseEmitter> streams =
        byContract.computeIfAbsent(contractId, id -> new CopyOnWriteArraySet<>());
    streams.add(emitter);
    Runnable remove = () -> remove(contractId, emitter);
    emitter.onCompletion(remove);
    emitter.onTimeout(remove);
    emitter.onError(e -> remove.run());
    if (current != null) {
      send(contractId, emitter, new StatusView(current.id(), current.status()));
    }
    return emitter;
  }

  @KafkaListener(
      topics = AnalysisStatusChanged.TOPIC,
      groupId = "mizan-api-status-${random.uuid}",
      properties = {
        "spring.json.value.default.type=ma.mizan.api.contracts.AnalysisStatusChanged",
        "auto.offset.reset=latest"
      })
  void onStatusChanged(AnalysisStatusChanged change) {
    Set<SseEmitter> streams = byContract.get(change.contractId());
    if (streams == null) {
      return;
    }
    StatusView view = new StatusView(change.analysisId(), change.status());
    streams.forEach(emitter -> send(change.contractId(), emitter, view));
  }

  /** Keeps idle connections open through proxies; also detects browsers that went away. */
  @Scheduled(fixedDelayString = "PT15S", initialDelayString = "PT15S")
  void heartbeat() {
    byContract.forEach(
        (contractId, streams) ->
            streams.forEach(
                emitter -> {
                  try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                  } catch (IOException | IllegalStateException gone) {
                    remove(contractId, emitter);
                  }
                }));
  }

  int openStreams(UUID contractId) {
    Set<SseEmitter> streams = byContract.get(contractId);
    return streams == null ? 0 : streams.size();
  }

  private void send(UUID contractId, SseEmitter emitter, StatusView view) {
    try {
      emitter.send(SseEmitter.event().name("status").data(view, MediaType.APPLICATION_JSON));
    } catch (IOException | IllegalStateException gone) {
      LOG.debug("Dropping a closed status stream for contract={}", contractId);
      remove(contractId, emitter);
    }
  }

  private void remove(UUID contractId, SseEmitter emitter) {
    byContract.computeIfPresent(
        contractId,
        (id, streams) -> {
          streams.remove(emitter);
          return streams.isEmpty() ? null : streams;
        });
  }

  /** What the browser receives: which analysis, and its new status. */
  record StatusView(UUID analysisId, AnalysisStatus status) {}
}
