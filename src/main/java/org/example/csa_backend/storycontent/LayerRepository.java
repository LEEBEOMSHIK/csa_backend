package org.example.csa_backend.storycontent;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LayerRepository extends JpaRepository<Layer, Long> {

    @Query("""
        select layer from Layer layer
        where layer.sceneId in :sceneIds
        order by layer.sceneId, layer.zIndex, layer.id
        """)
    List<Layer> findBySceneIdInOrder(@Param("sceneIds") Collection<Long> sceneIds);
}
