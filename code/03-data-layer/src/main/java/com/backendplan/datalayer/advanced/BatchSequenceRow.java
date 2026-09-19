package com.backendplan.datalayer.advanced;

import jakarta.persistence.*;

@Entity
@Table(name = "batch_sequence_rows")
public class BatchSequenceRow {
    @Id
    @SequenceGenerator(name = "seq_gen", sequenceName = "batch_sequence_rows_seq", allocationSize = 50)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_gen")
    Long id;

    String payload;

    public BatchSequenceRow() {}
    public BatchSequenceRow(String payload) { this.payload = payload; }
}
