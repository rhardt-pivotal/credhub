package io.pivotal.security.fake;

import io.pivotal.security.entity.OperationAuditRecord;
import io.pivotal.security.repository.AuditRecordRepository;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;

public class FakeAuditRecordRepository implements AuditRecordRepository {
  private final FakeTransactionManager transactionManager;
  private List<OperationAuditRecord> auditRecords;

  private boolean shouldThrow = false;

  public FakeAuditRecordRepository(FakeTransactionManager transactionManager) {
    this.transactionManager = transactionManager;
    this.auditRecords = new ArrayList<>();
  }

  @Override
  public <S extends OperationAuditRecord> S save(S entity) {
    transactionManager.currentTransaction.enqueue(() -> {
      OperationAuditRecord copy = new OperationAuditRecord(
          entity.getNow(),
          entity.getOperation(),
          entity.getUserId(),
          entity.getUserName(),
          entity.getUaaUrl(),
          entity.getTokenIssued(),
          entity.getTokenExpires(),
          entity.getHostName(),
          entity.getMethod(),
          entity.getPath(),
          entity.getRequesterIp(),
          entity.getXForwardedFor(),
          entity.getClientId(),
          entity.getScope(),
          entity.getGrantType()
      );
      copy.setStatusCode(entity.getStatusCode());
      if (!entity.isSuccess()) {
        copy.setFailed();
      }
      auditRecords.add(copy);
    });
    if (shouldThrow) throw new RuntimeException(getClass().getSimpleName());
    return entity;
  }

  @Override
  public OperationAuditRecord findOne(Long aLong) {
    return null;
  }

  @Override
  public boolean exists(Long aLong) {
    return false;
  }

  @Override
  public List<OperationAuditRecord> findAll() {
    return auditRecords;
  }

  @Override
  public List<OperationAuditRecord> findAll(Sort sort) {
    return null;
  }

  @Override
  public Page<OperationAuditRecord> findAll(Pageable pageable) {
    return null;
  }

  @Override
  public List<OperationAuditRecord> findAll(Iterable<Long> longs) {
    return null;
  }

  @Override
  public long count() {
    return auditRecords.size();
  }

  @Override
  public void delete(Long aLong) {

  }

  @Override
  public void delete(OperationAuditRecord entity) {

  }

  @Override
  public void delete(Iterable<? extends OperationAuditRecord> entities) {

  }

  @Override
  public void deleteAll() {

  }

  @Override
  public void flush() {

  }

  @Override
  public void deleteInBatch(Iterable<OperationAuditRecord> entities) {

  }

  @Override
  public void deleteAllInBatch() {

  }

  @Override
  public OperationAuditRecord getOne(Long aLong) {
    return null;
  }

  @Override
  public <S extends OperationAuditRecord> S saveAndFlush(S entity) {
    return null;
  }

  @Override
  public <S extends OperationAuditRecord> List<S> save(Iterable<S> entities) {
    return null;
  }

  public void failOnSave() {
    shouldThrow = true;
  }

  @Override
  public <S extends OperationAuditRecord> List<S> findAll(Example<S> example) {
    return null;
  }

  @Override
  public <S extends OperationAuditRecord> List<S> findAll(Example<S> example, Sort sort) {
    return null;
  }

  @Override
  public <S extends OperationAuditRecord> S findOne(Example<S> example) {
    return null;
  }

  @Override
  public <S extends OperationAuditRecord> Page<S> findAll(Example<S> example, Pageable pageable) {
    return null;
  }

  @Override
  public <S extends OperationAuditRecord> long count(Example<S> example) {
    return 0;
  }

  @Override
  public <S extends OperationAuditRecord> boolean exists(Example<S> example) {
    return false;
  }
}
