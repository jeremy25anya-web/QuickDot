package ca.amartian.quickdot.relay;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Active uploads by transferId (spec §5/§6). In-memory only, never persisted. */
@Component
public class FileTransferRegistry {

    private final ConcurrentHashMap<String, FileTransfer> transfers = new ConcurrentHashMap<>();

    public boolean start(FileTransfer transfer) {
        return transfers.putIfAbsent(transfer.transferId(), transfer) == null;
    }

    public Optional<FileTransfer> find(String transferId) {
        return Optional.ofNullable(transfers.get(transferId));
    }

    public void remove(String transferId) {
        transfers.remove(transferId);
    }

    /** Spec §6: 1 concurrent upload per connection. */
    public boolean hasActiveUpload(String participantId) {
        return transfers.values().stream().anyMatch(t -> t.senderId().equals(participantId));
    }
}
