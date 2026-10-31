package com.bofo.finder.model;

import static org.junit.Assert.*;

import org.json.JSONObject;
import org.junit.Test;

public class ModelsTest {
    @Test public void roleParsingDefaultsToStudent() {
        assertEquals(Role.STUDENT, Role.of(null));
        assertEquals(Role.OWNER, Role.of("owner"));
        assertEquals(Role.ADMIN, Role.of("ADMIN"));
        assertEquals("owner", Role.OWNER.db());
    }

    @Test public void boardingHouseReadsServerRowAndKeepsDistance() throws Exception {
        JSONObject o = new JSONObject("{\"id\":\"b1\",\"owner_id\":\"o1\",\"name\":\"Casa Verde\",\"lat\":9.31,\"lng\":123.30,"
                + "\"price\":2500,\"available_beds\":3,\"free_wifi\":true,\"avg_rating\":4.5,\"review_count\":8,"
                + "\"is_featured\":true,\"amenity_names\":[\"Wifi\",\"Laundry\"]}");
        BoardingHouse b = BoardingHouse.from(o);
        assertEquals("Casa Verde", b.name());
        assertEquals("", b.address());                    // missing text -> empty, not null
        assertEquals(2500, b.price(), 0);
        assertTrue(b.wifi());
        assertFalse(b.water());
        assertTrue(b.featured());
        assertEquals(2, b.amenities().size());
        BoardingHouse near = b.withDistance(1.25);
        assertEquals(1.25, near.distanceKm(), 0);
        assertEquals("b1", near.id());
    }

    @Test public void profileHandlesNullsFromTheServer() throws Exception {
        JSONObject o = new JSONObject("{\"id\":\"u1\",\"role\":\"owner\",\"is_verified\":true,\"username\":null,\"age\":null,\"region\":\"NIR\"}");
        Profile p = Profile.from(o);
        assertEquals(Role.OWNER, p.role());
        assertTrue(p.verified());
        assertEquals("", p.username());
        assertNull(p.age());
        assertEquals("NIR", p.region());
        assertNull(p.defaultCollegeId());
    }

    @Test public void messageTreatsNullReadAtAsUnread() throws Exception {
        Message m = Message.from(new JSONObject("{\"id\":\"m1\",\"conversation_id\":\"c1\",\"sender_id\":\"u1\",\"body\":\"hi\",\"created_at\":\"t\",\"read_at\":null}"));
        assertNull(m.readAt());
        assertEquals("hi", m.body());
    }

    @Test public void amenityLabelAddsIconWhenPresent() throws Exception {
        assertEquals("\uD83D\uDCF6 Wifi", Amenity.from(new JSONObject("{\"id\":\"a\",\"name\":\"Wifi\",\"icon\":\"\uD83D\uDCF6\",\"is_popular\":true}")).label());
        assertEquals("Wifi", new Amenity("a", "Wifi", " ", false, 0).label());
    }
}
