package com.joblivo.profile;

import com.joblivo.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class CareerProfileCertificationTest {

    @Test
    void testEntityTableAndIdMapping() throws NoSuchFieldException {
        Table table = CareerProfileCertification.class.getAnnotation(Table.class);
        assertNotNull(table);
        assertEquals("career_profile_certifications", table.name());

        Field idField = CareerProfileCertification.class.getDeclaredField("id");
        assertNotNull(idField.getAnnotation(Id.class));
        assertNotNull(idField.getAnnotation(GeneratedValue.class));

        Column idColumn = idField.getAnnotation(Column.class);
        assertNotNull(idColumn);
        assertEquals("id", idColumn.name());
        assertFalse(idColumn.nullable());
        assertFalse(idColumn.updatable());
    }

    @Test
    void testCareerProfileRelationshipMapping() throws NoSuchFieldException {
        Field profileField = CareerProfileCertification.class.getDeclaredField("careerProfile");
        ManyToOne manyToOne = profileField.getAnnotation(ManyToOne.class);
        assertNotNull(manyToOne);
        assertEquals(FetchType.LAZY, manyToOne.fetch());
        assertFalse(manyToOne.optional());

        JoinColumn joinColumn = profileField.getAnnotation(JoinColumn.class);
        assertNotNull(joinColumn);
        assertEquals("career_profile_id", joinColumn.name());
        assertFalse(joinColumn.nullable());
    }

    @Test
    void testColumnMappingsAndConstraints() throws NoSuchFieldException {
        Field nameField = CareerProfileCertification.class.getDeclaredField("certificationName");
        Column nameCol = nameField.getAnnotation(Column.class);
        assertNotNull(nameCol);
        assertEquals("certification_name", nameCol.name());
        assertEquals(150, nameCol.length());
        assertFalse(nameCol.nullable());

        Field orgField = CareerProfileCertification.class.getDeclaredField("issuingOrganization");
        Column orgCol = orgField.getAnnotation(Column.class);
        assertNotNull(orgCol);
        assertEquals("issuing_organization", orgCol.name());
        assertEquals(150, orgCol.length());
        assertFalse(orgCol.nullable());

        Field credIdField = CareerProfileCertification.class.getDeclaredField("credentialId");
        Column credIdCol = credIdField.getAnnotation(Column.class);
        assertNotNull(credIdCol);
        assertEquals("credential_id", credIdCol.name());
        assertEquals(100, credIdCol.length());

        Field credUrlField = CareerProfileCertification.class.getDeclaredField("credentialUrl");
        Column credUrlCol = credUrlField.getAnnotation(Column.class);
        assertNotNull(credUrlCol);
        assertEquals("credential_url", credUrlCol.name());
        assertEquals(500, credUrlCol.length());

        Field issueField = CareerProfileCertification.class.getDeclaredField("issueDate");
        Column issueCol = issueField.getAnnotation(Column.class);
        assertNotNull(issueCol);
        assertEquals("issue_date", issueCol.name());

        Field expField = CareerProfileCertification.class.getDeclaredField("expirationDate");
        Column expCol = expField.getAnnotation(Column.class);
        assertNotNull(expCol);
        assertEquals("expiration_date", expCol.name());

        Field noExpField = CareerProfileCertification.class.getDeclaredField("doesNotExpire");
        Column noExpCol = noExpField.getAnnotation(Column.class);
        assertNotNull(noExpCol);
        assertEquals("does_not_expire", noExpCol.name());
        assertFalse(noExpCol.nullable());

        Field descField = CareerProfileCertification.class.getDeclaredField("description");
        Column descCol = descField.getAnnotation(Column.class);
        assertNotNull(descCol);
        assertEquals("description", descCol.name());
        assertEquals("TEXT", descCol.columnDefinition());

        Field orderField = CareerProfileCertification.class.getDeclaredField("displayOrder");
        Column orderCol = orderField.getAnnotation(Column.class);
        assertNotNull(orderCol);
        assertEquals("display_order", orderCol.name());
        assertFalse(orderCol.nullable());

        Field createdField = CareerProfileCertification.class.getDeclaredField("createdAt");
        Column createdCol = createdField.getAnnotation(Column.class);
        assertNotNull(createdCol);
        assertEquals("created_at", createdCol.name());
        assertFalse(createdCol.nullable());
        assertFalse(createdCol.updatable());

        Field updatedField = CareerProfileCertification.class.getDeclaredField("updatedAt");
        Column updatedCol = updatedField.getAnnotation(Column.class);
        assertNotNull(updatedCol);
        assertEquals("updated_at", updatedCol.name());
        assertFalse(updatedCol.nullable());
    }

    @Test
    void testGettersAndSetters() {
        User user = new User("certuser@joblivo.com", "Cert User");
        CareerProfile profile = new CareerProfile(user);

        CareerProfileCertification cert = new CareerProfileCertification(
                profile,
                "  AWS Certified Solutions Architect – Associate  ",
                "  Amazon Web Services  ",
                "  AWS-123456  ",
                "  https://aws.amazon.com/verify/AWS-123456  ",
                LocalDate.of(2023, 1, 15),
                LocalDate.of(2026, 1, 15),
                false,
                "  Validation of cloud architecture skills  ",
                1
        );

        assertEquals(profile, cert.getCareerProfile());
        assertEquals("AWS Certified Solutions Architect – Associate", cert.getCertificationName());
        assertEquals("Amazon Web Services", cert.getIssuingOrganization());
        assertEquals("AWS-123456", cert.getCredentialId());
        assertEquals("https://aws.amazon.com/verify/AWS-123456", cert.getCredentialUrl());
        assertEquals(LocalDate.of(2023, 1, 15), cert.getIssueDate());
        assertEquals(LocalDate.of(2026, 1, 15), cert.getExpirationDate());
        assertFalse(cert.isDoesNotExpire());
        assertEquals("Validation of cloud architecture skills", cert.getDescription());
        assertEquals(1, cert.getDisplayOrder());

        // Update fields
        cert.setCertificationName("  Certified Kubernetes Administrator  ");
        cert.setIssuingOrganization("  CNCF  ");
        cert.setCredentialId(null);
        cert.setCredentialUrl(null);
        cert.setIssueDate(LocalDate.of(2024, 2, 1));
        cert.setExpirationDate(null);
        cert.setDoesNotExpire(true);
        cert.setDescription(null);
        cert.setDisplayOrder(0);

        assertEquals("Certified Kubernetes Administrator", cert.getCertificationName());
        assertEquals("CNCF", cert.getIssuingOrganization());
        assertNull(cert.getCredentialId());
        assertNull(cert.getCredentialUrl());
        assertEquals(LocalDate.of(2024, 2, 1), cert.getIssueDate());
        assertNull(cert.getExpirationDate());
        assertTrue(cert.isDoesNotExpire());
        assertNull(cert.getDescription());
        assertEquals(0, cert.getDisplayOrder());
    }

    @Test
    void testLifecycleCallbacks() throws Exception {
        Method onCreate = CareerProfileCertification.class.getDeclaredMethod("onCreate");
        onCreate.setAccessible(true);
        Method onUpdate = CareerProfileCertification.class.getDeclaredMethod("onUpdate");
        onUpdate.setAccessible(true);

        assertNotNull(onCreate.getAnnotation(PrePersist.class));
        assertNotNull(onUpdate.getAnnotation(PreUpdate.class));

        User user = new User("certuser@joblivo.com", "Cert User");
        CareerProfile profile = new CareerProfile(user);
        CareerProfileCertification cert = new CareerProfileCertification(
                profile, "CKA", "Linux Foundation", null, null, null, null, true, null, 0
        );

        assertNull(cert.getCreatedAt());
        assertNull(cert.getUpdatedAt());

        onCreate.invoke(cert);
        assertNotNull(cert.getCreatedAt());
        assertNotNull(cert.getUpdatedAt());

        Instant originalUpdatedAt = cert.getUpdatedAt();
        Thread.sleep(2);
        onUpdate.invoke(cert);
        assertTrue(cert.getUpdatedAt().isAfter(originalUpdatedAt) || cert.getUpdatedAt().equals(originalUpdatedAt));
    }

    @Test
    void testEqualsAndHashCode() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        User user = new User("certuser@joblivo.com", "Cert User");
        CareerProfile profile = new CareerProfile(user);

        CareerProfileCertification c1 = new CareerProfileCertification(
                id1, profile, "AWS CSA", "AWS", "123", "https://aws.com",
                LocalDate.of(2023, 1, 1), null, true, "Desc", 0, Instant.now(), Instant.now()
        );
        CareerProfileCertification c1Same = new CareerProfileCertification(
                id1, profile, "CKA", "CNCF", "456", "https://cncf.io",
                LocalDate.of(2024, 1, 1), null, true, "Desc", 0, Instant.now(), Instant.now()
        );
        CareerProfileCertification c2 = new CareerProfileCertification(
                id2, profile, "AWS CSA", "AWS", "123", "https://aws.com",
                LocalDate.of(2023, 1, 1), null, true, "Desc", 0, Instant.now(), Instant.now()
        );

        assertEquals(c1, c1Same);
        assertEquals(c1.hashCode(), c1Same.hashCode());
        assertNotEquals(c1, c2);
        assertNotEquals(c1, null);
        assertNotEquals(c1, new Object());
    }

    @Test
    void testRequestNormalization() {
        CertificationRequest request = new CertificationRequest(
                "   AWS Certified Developer   ",
                "   Amazon Web Services   ",
                "   AWS-DEV-999   ",
                "   https://aws.amazon.com/verify   ",
                LocalDate.of(2023, 5, 1),
                LocalDate.of(2026, 5, 1),
                null,
                "   Associate level certification   ",
                null
        );

        assertEquals("AWS Certified Developer", request.certificationName());
        assertEquals("Amazon Web Services", request.issuingOrganization());
        assertEquals("AWS-DEV-999", request.credentialId());
        assertEquals("https://aws.amazon.com/verify", request.credentialUrl());
        assertFalse(request.doesNotExpire());
        assertEquals("Associate level certification", request.description());
        assertEquals(0, request.displayOrder());
    }

    @Test
    void testDateRangeValidationRule() {
        CertificationRequest invalidDates = new CertificationRequest(
                "Cert", "Org", null, null,
                LocalDate.of(2025, 5, 1),
                LocalDate.of(2024, 5, 1),
                false, null, 0
        );
        assertFalse(invalidDates.isDateRangeValid());

        CertificationRequest sameDates = new CertificationRequest(
                "Cert", "Org", null, null,
                LocalDate.of(2025, 5, 1),
                LocalDate.of(2025, 5, 1),
                false, null, 0
        );
        assertTrue(sameDates.isDateRangeValid());

        CertificationRequest validDates = new CertificationRequest(
                "Cert", "Org", null, null,
                LocalDate.of(2024, 5, 1),
                LocalDate.of(2025, 5, 1),
                false, null, 0
        );
        assertTrue(validDates.isDateRangeValid());

        CertificationRequest nullDates = new CertificationRequest(
                "Cert", "Org", null, null,
                null, null, false, null, 0
        );
        assertTrue(nullDates.isDateRangeValid());
    }

    @Test
    void testDoesNotExpireConsistencyRule() {
        CertificationRequest invalidExpiring = new CertificationRequest(
                "Cert", "Org", null, null,
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2026, 1, 1),
                true, null, 0
        );
        assertFalse(invalidExpiring.isDoesNotExpireValid());

        CertificationRequest validNonExpiring = new CertificationRequest(
                "Cert", "Org", null, null,
                LocalDate.of(2023, 1, 1),
                null,
                true, null, 0
        );
        assertTrue(validNonExpiring.isDoesNotExpireValid());

        CertificationRequest validExpiring = new CertificationRequest(
                "Cert", "Org", null, null,
                LocalDate.of(2023, 1, 1),
                LocalDate.of(2026, 1, 1),
                false, null, 0
        );
        assertTrue(validExpiring.isDoesNotExpireValid());
    }

    @Test
    void testResponseMapping() {
        UUID id = UUID.randomUUID();
        User user = new User("certuser@joblivo.com", "Cert User");
        CareerProfile profile = new CareerProfile(user);
        Instant now = Instant.now();

        CareerProfileCertification cert = new CareerProfileCertification(
                id, profile, "GCP Professional Cloud Architect", "Google Cloud",
                "GCP-777", "https://google.com/verify/777",
                LocalDate.of(2023, 6, 1), LocalDate.of(2025, 6, 1), false,
                "Cloud architecture certification", 2, now, now
        );

        CertificationResponse response = CertificationResponse.from(cert);
        assertEquals(id, response.id());
        assertEquals("GCP Professional Cloud Architect", response.certificationName());
        assertEquals("Google Cloud", response.issuingOrganization());
        assertEquals("GCP-777", response.credentialId());
        assertEquals("https://google.com/verify/777", response.credentialUrl());
        assertEquals(LocalDate.of(2023, 6, 1), response.issueDate());
        assertEquals(LocalDate.of(2025, 6, 1), response.expirationDate());
        assertFalse(response.doesNotExpire());
        assertEquals("Cloud architecture certification", response.description());
        assertEquals(2, response.displayOrder());
        assertEquals(now, response.createdAt());
        assertEquals(now, response.updatedAt());
    }

    @Test
    void testScopeProtectionNoFuturePromptFields() {
        List<String> prohibitedScopeFields = List.of(
                "vault", "evidence", "verified", "verifier", "resume",
                "score", "salary", "compensation", "interview"
        );

        for (Field field : CareerProfileCertification.class.getDeclaredFields()) {
            String lowerName = field.getName().toLowerCase();
            for (String prohibited : prohibitedScopeFields) {
                assertFalse(
                        lowerName.contains(prohibited),
                        "CareerProfileCertification entity must not contain out-of-scope field: " + field.getName()
                );
            }
        }
    }
}
