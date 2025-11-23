package bj.gouv.sgg.repository;

import bj.gouv.sgg.model.FetchResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository pour gérer les résultats de fetch en base de données
 */
@Repository
public interface FetchResultRepository extends JpaRepository<FetchResult, Long> {
    
    /**
     * Trouve un résultat par son documentId
     */
    Optional<FetchResult> findByDocumentId(String documentId);
    
    /**
     * Vérifie si un document existe par son documentId
     */
    boolean existsByDocumentId(String documentId);
    
    /**
     * Récupère tous les documents par type
     */
    List<FetchResult> findByDocumentType(String documentType);
    
    /**
     * Récupère tous les documents par année
     */
    List<FetchResult> findByYear(Integer year);
    
    /**
     * Compte le nombre de documents par type
     */
    long countByDocumentType(String documentType);
    
    /**
     * Compte le nombre de documents par année
     */
    long countByYear(Integer year);
    
    /**
     * Récupère les URLs des documents trouvés
     */
    @Query("SELECT f.url FROM FetchResult f")
    List<String> findAllFoundUrls();
    
    /**
     * Récupère uniquement les documentIds (optimisé pour le cache)
     */
    @Query("SELECT f.documentId FROM FetchResult f")
    List<String> findAllDocumentIds();
    
    /**
     * Récupère uniquement les documentIds des documents trouvés
     * Permet d'exclure les documents déjà vérifiés
     */
    @Query("SELECT f.documentId FROM FetchResult f")
    List<String> findFoundDocumentIds();
    
    /**
     * Trouve les documents par statut
     */
    List<FetchResult> findByStatus(String status);
}
