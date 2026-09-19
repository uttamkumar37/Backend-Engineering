package com.backendplan.datalayer.advanced;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BatchInsertService {
    private final BatchIdentityRowRepository identityRepo;
    private final BatchSequenceRowRepository sequenceRepo;

    public BatchInsertService(BatchIdentityRowRepository identityRepo, BatchSequenceRowRepository sequenceRepo) {
        this.identityRepo = identityRepo;
        this.sequenceRepo = sequenceRepo;
    }

    @Transactional
    public void insertIdentityRows(int count) {
        for (int i = 0; i < count; i++) {
            identityRepo.save(new BatchIdentityRow("payload-" + i));
        }
    }

    @Transactional
    public void insertSequenceRows(int count) {
        for (int i = 0; i < count; i++) {
            sequenceRepo.save(new BatchSequenceRow("payload-" + i));
        }
    }
}
