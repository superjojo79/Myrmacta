package be.myrmacta.infrastructure.persistence.workflow;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTaskQuestionAnswerRepository extends JpaRepository<TaskQuestionAnswerJpaEntity, UUID> {

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query(
            "delete from TaskQuestionAnswerJpaEntity e where e.dossierId = :dossierId")
    int deleteAllByDossierId(@org.springframework.data.repository.query.Param("dossierId") UUID dossierId);
}
