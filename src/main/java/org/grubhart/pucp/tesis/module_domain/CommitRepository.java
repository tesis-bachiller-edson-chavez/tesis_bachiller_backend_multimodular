package org.grubhart.pucp.tesis.module_domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommitRepository extends JpaRepository<Commit, String> {

    Optional<Commit> findBySha(String sha);

    Optional<Commit> findByRepositoryIdAndSha(Long repositoryId, String sha);

    Optional<Commit> findFirstByOrderByDateDesc();

    @Query(value = "WITH RECURSIVE commit_graph AS ( " +
            "    SELECT :endSha as sha " +
            "    UNION " +
            "    SELECT cp.parent_sha " +
            "    FROM commit_parent cp " +
            "    JOIN commit_graph cg ON cp.commit_sha = cg.sha " +
            "    WHERE cp.parent_sha IS NOT NULL AND cp.parent_sha != :startSha " +
            ") " +
            "SELECT c.sha, c.author, c.message, c.date FROM commit c WHERE c.sha IN (SELECT sha FROM commit_graph) AND c.sha != :startSha", nativeQuery = true)
    List<Commit> findCommitsBetween(@Param("startSha") String startSha, @Param("endSha") String endSha);

    @Query(value = "WITH RECURSIVE commit_graph AS ( " +
            "    SELECT :sha as sha " +
            "    UNION " +
            "    SELECT cp.parent_sha " +
            "    FROM commit_parent cp " +
            "    JOIN commit_graph cg ON cp.commit_sha = cg.sha " +
            "    WHERE cp.parent_sha IS NOT NULL " +
            ") " +
            "SELECT c.sha, c.author, c.message, c.date FROM commit c WHERE c.sha IN (SELECT sha FROM commit_graph)", nativeQuery = true)
    List<Commit> findAllParentsOf(@Param("sha") String sha);

}
