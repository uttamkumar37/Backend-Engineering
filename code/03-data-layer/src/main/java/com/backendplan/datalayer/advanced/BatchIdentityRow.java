package com.backendplan.datalayer.advanced;

import jakarta.persistence.*;

@Entity
@Table(name = "batch_identity_rows")
public class BatchIdentityRow {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    String payload;

    public BatchIdentityRow() {}
    public BatchIdentityRow(String payload) { this.payload = payload; }
}
