package com.example.analyzeproject.infrastructure.persistence;

import com.example.analyzeproject.domain.model.*;
import com.example.analyzeproject.infrastructure.persistence.jpa.PersistenceMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PersistenceMapperTest {
    private final PersistenceMapper mapper = new PersistenceMapper();
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test void persistenceRoundTripKeepsApiFieldsQualityAndAuditWithoutAliasing() {
        var draw = new LotteryNumber(LocalDate.of(2026,10,29), "POWER", new ArrayList<>(List.of(9,29,33,38,45,46)), 53, "source");
        draw.setId(42L); draw.setCreatedAt(LocalDateTime.of(2026,10,29,19,0));
        Map<String,Object> before = new LinkedHashMap<>(); before.put("note",null); before.put("numbers",new ArrayList<>(draw.getNumbers()));
        draw.setQuality(new LinkedHashMap<>(Map.of("status","SOURCE_MATCHED","audit",new ArrayList<>(List.of(before)))));
        var entity = mapper.toEntity(draw);
        var restored = mapper.toDomain(entity);
        assertEquals(json.valueToTree(draw), json.valueToTree(restored));
        assertNotSame(draw.getNumbers(), entity.getNumbers());
        assertNotSame(entity.getNumbers(), restored.getNumbers());
        assertNotSame(draw.getQuality(), entity.getQuality());
        assertNotSame(draw.getQuality().get("audit"), entity.getQuality().get("audit"));
        assertNotSame(entity.getQuality().get("audit"), restored.getQuality().get("audit"));
        draw.getNumbers().set(0,1); before.put("note","changed");
        assertEquals(9, restored.getNumbers().get(0));
        @SuppressWarnings("unchecked") var audit = (List<Map<String,Object>>) restored.getQuality().get("audit");
        assertNull(audit.get(0).get("note"));
        assertNull(mapper.toEntity((LotteryNumber)null));
        draw.setNumbers(null); draw.setQuality(null);
        assertNull(mapper.toDomain(mapper.toEntity(draw)).getNumbers());
        assertNull(mapper.toDomain(mapper.toEntity(draw)).getQuality());
    }

    @Test void allOtherPersistenceModelsRetainEverySerializedField() {
        var ticket = new UserTicket(); ticket.setId(9L); ticket.setCategory("POWER"); ticket.setDrawDate("2026-10-29");
        ticket.setNumbers(new ArrayList<>(List.of(1,2,3,4,5,6))); ticket.setPrize("NONE"); ticket.setCheckedAt(LocalDateTime.of(2026,10,29,20,0));
        assertEquals(json.valueToTree(ticket), json.valueToTree(mapper.toDomain(mapper.toEntity(ticket))));
        var parameters = new AlgorithmHyperparameter("v1","2026-10-29","POWER","model","{}","readme","note"); parameters.setId(2L);
        assertEquals(json.valueToTree(parameters), json.valueToTree(mapper.toDomain(mapper.toEntity(parameters))));
        var deviation = new LotteryDeviationVariable("POWER","2026-10-27","2026-10-29",1,2,1,"NEIGHBOR_DRIFT","pattern",0.2,"rule","note"); deviation.setId(3L);
        assertEquals(json.valueToTree(deviation), json.valueToTree(mapper.toDomain(mapper.toEntity(deviation))));
    }
}
