/**
 * Copyright (c) Codice Foundation
 * <p>
 * This is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or any later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details. A copy of the GNU Lesser General Public License
 * is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 **/
package ddf.catalog.registry.transformer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.joda.time.format.ISODateTimeFormat.dateOptionalTimeParser;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

import javax.xml.bind.JAXBElement;

import org.apache.commons.io.IOUtils;
import org.codice.ddf.parser.Parser;
import org.codice.ddf.parser.ParserConfigurator;
import org.codice.ddf.parser.xml.XmlParser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

import ddf.catalog.data.Metacard;
import ddf.catalog.data.impl.AttributeImpl;
import ddf.catalog.registry.common.metacard.RegistryMetacardImpl;
import ddf.catalog.registry.common.metacard.RegistryObjectMetacardType;
import ddf.catalog.transform.CatalogTransformerException;
import oasis.names.tc.ebxml_regrep.xsd.rim._3.RegistryObjectType;

@RunWith(JUnit4.class)
public class RegistryTransformerTest {

    private RegistryTransformer rit;

    private Parser parser;

    private void assertRegistryMetacard(Metacard meta) {
        assertThat(meta.getContentTypeName(),
                startsWith(RegistryObjectMetacardType.REGISTRY_METACARD_TYPE_NAME));
    }

    @Before
    public void setUp() {
        rit = new RegistryTransformer();
        parser = new XmlParser();
        rit.setParser(parser);

    }

    @Test(expected = CatalogTransformerException.class)
    public void testBadInputStream() throws Exception {
        InputStream is = Mockito.mock(InputStream.class);
        doThrow(new IOException()).when(is)
                .read(any());
        rit.transform(is);
    }

    @Test(expected = CatalogTransformerException.class)
    public void testParserReturnNull() throws Exception {
        Parser mockParser = Mockito.mock(Parser.class);
        when((mockParser).unmarshal(any(ParserConfigurator.class),
                any(Class.class),
                any(InputStream.class))).thenReturn(null);
        rit.setParser(mockParser);

        convert("/csw-basic-info.xml");
    }

    @Test(expected = CatalogTransformerException.class)
    public void testJaxbElementNullValue() throws Exception {
        JAXBElement<RegistryObjectType> mockRegistryObjectType = Mockito.mock(JAXBElement.class);
        when((mockRegistryObjectType).getValue()).thenReturn(null);

        Parser mockParser = Mockito.mock(Parser.class);
        when((mockParser).unmarshal(any(ParserConfigurator.class),
                any(Class.class),
                any(InputStream.class))).thenReturn(mockRegistryObjectType);

        rit.setParser(mockParser);
        convert("/csw-basic-info.xml");
    }

    @Test
    public void testBasicTransformWithoutId() throws Exception {
        assertRegistryMetacard(convert("/csw-rim-service.xml"));
    }

    @Test
    public void testBasicTransformWithId() throws Exception {
        InputStream is = getClass().getResourceAsStream("/csw-rim-service.xml");
        Metacard meta = rit.transform(is, "my-id");
        assertRegistryMetacard(meta);
    }

    @Test
    public void testBasicInfo() throws Exception {
        RegistryMetacardImpl meta = convert("/csw-basic-info.xml");
        assertRegistryMetacard(meta);

        assertThat(meta.getId(), is("2014ca7f59ac46f495e32b4a67a51276"));
        assertThat(meta.getTitle(), is("my service"));
        assertThat(meta.getDescription(), is("something"));
        assertThat(meta.getContentTypeVersion(), is("0.0.0"));
    }

    @Test
    public void testOrgInfo() throws Exception {
        RegistryMetacardImpl meta = convert("/csw-org-info.xml");
        assertRegistryMetacard(meta);

        assertThat(meta.getOrgName(), is("Codice"));
        assertThat(meta.getOrgAddress(), is("1234 Some Street, Phoenix, AZ 85037, USA"));
        assertThat(meta.getOrgPhoneNumber(), is("(555) 555-5555 extension 1234"));
        assertThat(meta.getOrgEmail(), is("emailaddress@something.com"));
    }

    @Test(expected = CatalogTransformerException.class)
    public void testBadBindingService() throws Exception {
        convert("/bad-binding-service.xml");
    }

    @Test(expected = CatalogTransformerException.class)
    public void testNoRegistryObjetId() throws Exception {
        convert("/bad-id.xml");
    }

    @Test(expected = CatalogTransformerException.class)
    public void testNoServiceId() throws Exception {
        convert("/bad-id-wrapped-service.xml");
    }

    @Test(expected = CatalogTransformerException.class)
    public void testNoPersonId() throws Exception {
        convert("/bad-id-wrapped-person.xml");
    }

    @Test(expected = CatalogTransformerException.class)
    public void testNoOrgId() throws Exception {
        convert("/bad-id-wrapped-org.xml");
    }

    @Test(expected = CatalogTransformerException.class)
    public void testParserException() throws Exception {
        convert("/bad-xml.xml");
    }

    @Test
    public void testCustomSlotSaved() throws Exception {
        // Just test that an unknown slot gets saved to the metadata field and not discarded.
        assertThat(convert("/custom-slot-service.xml").getMetadata()
                .contains("unknowSlotName"), is(true));
    }

    @Test
    public void testServiceWithMinimumBinding() throws Exception {
        RegistryMetacardImpl m = convert("/valid-federation-min-service.xml");
        assertThat(m.getAttribute(RegistryObjectMetacardType.SERVICE_BINDING_TYPES)
                .getValue(), is("csw"));
    }

    @Test
    public void testServiceWithMultipleBindings() throws Exception {
        RegistryMetacardImpl m = convert("/valid-federation-multiple-service.xml");
        List<Serializable> serviceBindingTypes =
                m.getAttribute(RegistryObjectMetacardType.SERVICE_BINDING_TYPES)
                        .getValues();
        assertThat(serviceBindingTypes.size(), is(2));
        assertThat(serviceBindingTypes, hasItem("csw"));
        assertThat(serviceBindingTypes, hasItem("soap"));
    }

    @Test
    public void testMinimumValidService() throws Exception {
        convert("/empty-service.xml");
    }

    @Test
    public void testMetacardToXml() throws Exception {
        String in = IOUtils.toString(getClass().getResourceAsStream("/csw-rim-service.xml"));
        Metacard m = rit.transform(IOUtils.toInputStream(in));
        String out = IOUtils.toString(rit.transform(m, null)
                .getInputStream());
        assertThat(in, is(out));
    }

    @Test(expected = CatalogTransformerException.class)
    public void testMetacardToXmlBadContentType() throws Exception {
        String in = IOUtils.toString(getClass().getResourceAsStream("/csw-rim-service.xml"));
        Metacard m = rit.transform(IOUtils.toInputStream(in));

        m.setAttribute(new AttributeImpl(Metacard.CONTENT_TYPE, "JustSomeMadeUpStuf"));
        String out = IOUtils.toString(rit.transform(m, null)
                .getInputStream());
        assertThat(in, is(out));
    }

    @Test
    public void testLastUpdated() throws Exception {
        RegistryMetacardImpl m = convert("/csw-last-updated.xml");
        String utc = m.getModifiedDate()
                .toInstant()
                .toString();
        assertThat(utc, is("2016-01-26T17:16:34.996Z"));
    }

    @Test(expected = CatalogTransformerException.class)
    public void testBadGeometryConversion() throws Exception {
        convert("/bad-geo-conversion.xml");
    }

    @Test
    public void testPersonNoExtension() throws Exception {
        RegistryMetacardImpl metacard = convert("/csw-person-info.xml");

        assertThat(metacard.getAttribute(Metacard.POINT_OF_CONTACT)
                .getValue(), is("Vito Andolini, (999) 555-2368, godfather@mafia.com"));
    }

    @Test
    public void testServiceBindingWithMultipleTypes() throws Exception {
        RegistryMetacardImpl metacard = convert("/binding-service-multiple-types.xml");

        List<Serializable> serializableList =
                metacard.getAttribute(RegistryObjectMetacardType.SERVICE_BINDING_TYPES)
                        .getValues();
        assertThat(serializableList.size(), is(2));
        assertThat(serializableList, hasItem("csw"));
        assertThat(serializableList, hasItem("fakeBindingType"));
    }

    @Test
    public void testFullRegistryPackage() throws Exception {
        RegistryMetacardImpl metacard = convert("/csw-full-registry-package.xml");

        Date date = dateOptionalTimeParser().parseDateTime("2015-06-01T13:15:30Z")
                .toDate();
        assertThat(metacard.getAttribute(RegistryObjectMetacardType.LIVE_DATE)
                .getValue(), is(date));

        date = dateOptionalTimeParser().parseDateTime("2015-11-01T13:15:30Z")
                .toDate();
        assertThat(metacard.getAttribute(RegistryObjectMetacardType.DATA_START_DATE)
                .getValue(), is(date));

        date = dateOptionalTimeParser().parseDateTime("2015-12-01T23:01:40Z")
                .toDate();
        assertThat(metacard.getAttribute(RegistryObjectMetacardType.DATA_END_DATE)
                .getValue(), is(date));

        date = dateOptionalTimeParser().parseDateTime("2016-01-26T17:16:34.996Z")
                .toDate();
        assertThat(metacard.getAttribute(Metacard.MODIFIED)
                .getValue(), is(date));

        assertThat(metacard.getAttribute(RegistryObjectMetacardType.LINKS)
                .getValue(), is("https://some/link/to/my/repo"));

        assertThat(metacard.getAttribute(Metacard.GEOGRAPHY)
                .getValue(), is("POINT (112.267472 33.467944)"));
        assertThat(metacard.getAttribute(RegistryObjectMetacardType.REGION)
                .getValue(), is("USA"));

        List<Serializable> serializableList =
                metacard.getAttribute(RegistryObjectMetacardType.DATA_SOURCES)
                        .getValues();
        assertThat(serializableList.size(), is(2));
        assertThat(serializableList, hasItem("youtube"));
        assertThat(serializableList, hasItem("myCamera"));

        serializableList = metacard.getAttribute(RegistryObjectMetacardType.DATA_TYPES)
                .getValues();
        assertThat(serializableList.size(), is(2));
        assertThat(serializableList, hasItem("video"));
        assertThat(serializableList, hasItem("sensor"));

        assertThat(metacard.getAttribute(RegistryObjectMetacardType.SECURITY_LEVEL)
                .getValue(), is("role=guest"));
        assertThat(metacard.getAttribute(Metacard.TITLE)
                .getValue(), is("Node Name"));
        assertThat(metacard.getAttribute(Metacard.DESCRIPTION)
                .getValue(), is(
                "A little something describing this node in less than 1024 characters"));
        assertThat(metacard.getAttribute(Metacard.CONTENT_TYPE_VERSION)
                .getValue(), is("2.9.x"));

        serializableList = metacard.getAttribute(RegistryObjectMetacardType.SERVICE_BINDING_TYPES)
                .getValues();
        assertThat(serializableList.size(), is(2));
        assertThat(serializableList, hasItem("csw"));
        assertThat(serializableList, hasItem("soap13"));

        serializableList = metacard.getAttribute(RegistryObjectMetacardType.SERVICE_BINDINGS)
                .getValues();
        assertThat(serializableList.size(), is(2));
        assertThat(serializableList, hasItem("REST"));
        assertThat(serializableList, hasItem("SOAP"));

        assertThat(metacard.getAttribute(RegistryObjectMetacardType.ORGANIZATION_NAME)
                .getValue(), is("Codice"));
        assertThat(metacard.getAttribute(RegistryObjectMetacardType.ORGANIZATION_EMAIL)
                .getValue(), is("emailaddress@something.com"));
        assertThat(metacard.getAttribute(RegistryObjectMetacardType.ORGANIZATION_PHONE_NUMBER)
                .getValue(), is("(555) 555-5555 extension 1234"));
        assertThat(metacard.getAttribute(RegistryObjectMetacardType.ORGANIZATION_ADDRESS)
                .getValue(), is("1234 Some Street, Phoenix, AZ 85037, USA"));

        assertThat(metacard.getAttribute(Metacard.POINT_OF_CONTACT)
                .getValue(), is(
                "john doe, (111) 111-1111 extension 1234, emailaddress@something.com"));
    }

    @Test
    public void testRearrangedRegistryPackage() throws Exception {
        RegistryMetacardImpl metacard = convert("/csw-registry-package-rearranged.xml");

        Date date = dateOptionalTimeParser().parseDateTime("2015-06-01T13:15:30Z")
                .toDate();
        assertThat(metacard.getAttribute(RegistryObjectMetacardType.LIVE_DATE)
                .getValue(), is(date));

        date = dateOptionalTimeParser().parseDateTime("2015-11-01T13:15:30Z")
                .toDate();
        assertThat(metacard.getAttribute(RegistryObjectMetacardType.DATA_START_DATE)
                .getValue(), is(date));

        date = dateOptionalTimeParser().parseDateTime("2015-12-01T23:01:40Z")
                .toDate();
        assertThat(metacard.getAttribute(RegistryObjectMetacardType.DATA_END_DATE)
                .getValue(), is(date));

        date = dateOptionalTimeParser().parseDateTime("2016-01-26T17:16:34.996Z")
                .toDate();
        assertThat(metacard.getAttribute(Metacard.MODIFIED)
                .getValue(), is(date));

        assertThat(metacard.getAttribute(RegistryObjectMetacardType.LINKS)
                .getValue(), is("https://some/link/to/my/repo"));

        assertThat(metacard.getAttribute(Metacard.GEOGRAPHY)
                .getValue(), is("POINT (112.267472 33.467944)"));
        assertThat(metacard.getAttribute(RegistryObjectMetacardType.REGION)
                .getValue(), is("USA"));

        List<Serializable> serializableList =
                metacard.getAttribute(RegistryObjectMetacardType.DATA_SOURCES)
                        .getValues();
        assertThat(serializableList.size(), is(2));
        assertThat(serializableList, hasItem("youtube"));
        assertThat(serializableList, hasItem("myCamera"));

        serializableList = metacard.getAttribute(RegistryObjectMetacardType.DATA_TYPES)
                .getValues();
        assertThat(serializableList.size(), is(2));
        assertThat(serializableList, hasItem("video"));
        assertThat(serializableList, hasItem("sensor"));

        assertThat(metacard.getAttribute(RegistryObjectMetacardType.SECURITY_LEVEL)
                .getValue(), is("role=guest"));
        assertThat(metacard.getAttribute(Metacard.TITLE)
                .getValue(), is("Node Name"));
        assertThat(metacard.getAttribute(Metacard.DESCRIPTION)
                .getValue(), is(
                "A little something describing this node in less than 1024 characters"));
        assertThat(metacard.getAttribute(Metacard.CONTENT_TYPE_VERSION)
                .getValue(), is("2.9.x"));

        serializableList = metacard.getAttribute(RegistryObjectMetacardType.SERVICE_BINDING_TYPES)
                .getValues();
        assertThat(serializableList.size(), is(2));
        assertThat(serializableList, hasItem("csw"));
        assertThat(serializableList, hasItem("soap13"));

        serializableList = metacard.getAttribute(RegistryObjectMetacardType.SERVICE_BINDINGS)
                .getValues();
        assertThat(serializableList.size(), is(2));
        assertThat(serializableList, hasItem("REST"));
        assertThat(serializableList, hasItem("SOAP"));

        assertThat(metacard.getAttribute(RegistryObjectMetacardType.ORGANIZATION_NAME)
                .getValue(), is("Codice"));
        assertThat(metacard.getAttribute(RegistryObjectMetacardType.ORGANIZATION_EMAIL)
                .getValue(), is("emailaddress@something.com"));
        assertThat(metacard.getAttribute(RegistryObjectMetacardType.ORGANIZATION_PHONE_NUMBER)
                .getValue(), is("(555) 555-5555 extension 1234"));
        assertThat(metacard.getAttribute(RegistryObjectMetacardType.ORGANIZATION_ADDRESS)
                .getValue(), is("1234 Some Street, Phoenix, AZ 85037, USA"));

        assertThat(metacard.getAttribute(Metacard.POINT_OF_CONTACT)
                .getValue(), is(
                "john doe, (111) 111-1111 extension 1234, emailaddress@something.com"));
    }

    @Test
    public void testMultipleOrgsOneEmpty() throws Exception {
        RegistryMetacardImpl meta = convert("/multiple-org-one-empty-wrapped.xml");
        assertRegistryMetacard(meta);

        assertThat(meta.getOrgName(), is(nullValue()));
        assertThat(meta.getOrgAddress(), is(nullValue()));
        assertThat(meta.getOrgPhoneNumber(), is(nullValue()));
        assertThat(meta.getOrgEmail(), is(nullValue()));
    }

    @Test
    public void testMultiplePersonsOneEmpty() throws Exception {
        RegistryMetacardImpl meta = convert("/multiple-person-one-empty-wrapped.xml");
        assertRegistryMetacard(meta);

        assertThat(meta.getPointOfContact(), is("no name, no telephone number, no email address"));
    }

    private RegistryMetacardImpl convert(String path) throws Exception {
        return (RegistryMetacardImpl) rit.transform(getClass().getResourceAsStream(path));
    }
}
