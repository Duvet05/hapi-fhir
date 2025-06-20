package org.hl7.fhir.dstu3.hapi.validation;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.test.BaseTest;
import ca.uhn.fhir.test.utilities.LoggingExtension;
import ca.uhn.fhir.util.ClasspathUtil;
import ca.uhn.fhir.util.TestUtil;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import com.google.common.base.Charsets;
import org.apache.commons.io.IOUtils;
import org.hl7.fhir.MockValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.hl7.fhir.dstu3.fhirpath.FHIRPathEngine;
import org.hl7.fhir.dstu3.hapi.ctx.HapiWorkerContext;
import org.hl7.fhir.dstu3.model.Base;
import org.hl7.fhir.dstu3.model.BooleanType;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.dstu3.model.CodeSystem;
import org.hl7.fhir.dstu3.model.CodeType;
import org.hl7.fhir.dstu3.model.CodeableConcept;
import org.hl7.fhir.dstu3.model.Coding;
import org.hl7.fhir.dstu3.model.ContactPoint;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.Enumerations.PublicationStatus;
import org.hl7.fhir.dstu3.model.Extension;
import org.hl7.fhir.dstu3.model.Goal;
import org.hl7.fhir.dstu3.model.ImagingStudy;
import org.hl7.fhir.dstu3.model.Observation;
import org.hl7.fhir.dstu3.model.Observation.ObservationStatus;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.dstu3.model.Period;
import org.hl7.fhir.dstu3.model.Procedure;
import org.hl7.fhir.dstu3.model.Questionnaire;
import org.hl7.fhir.dstu3.model.Questionnaire.QuestionnaireItemComponent;
import org.hl7.fhir.dstu3.model.Questionnaire.QuestionnaireItemType;
import org.hl7.fhir.dstu3.model.QuestionnaireResponse;
import org.hl7.fhir.dstu3.model.Reference;
import org.hl7.fhir.dstu3.model.RelatedPerson;
import org.hl7.fhir.dstu3.model.StringType;
import org.hl7.fhir.dstu3.model.StructureDefinition;
import org.hl7.fhir.dstu3.model.StructureDefinition.StructureDefinitionKind;
import org.hl7.fhir.dstu3.model.ValueSet;
import org.hl7.fhir.dstu3.model.ValueSet.ValueSetExpansionComponent;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r5.utils.validation.IValidationPolicyAdvisor;
import org.hl7.fhir.r5.utils.validation.IValidatorResourceFetcher;
import org.hl7.fhir.r5.utils.validation.constants.ReferenceValidationPolicy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FhirInstanceValidatorDstu3Test extends BaseTest {

	private static final org.slf4j.Logger ourLog = org.slf4j.LoggerFactory.getLogger(FhirInstanceValidatorDstu3Test.class);
	private static FhirContext ourCtx = FhirContext.forDstu3Cached();
	@RegisterExtension
	public LoggingExtension myLoggingExtension = new LoggingExtension();
	private FhirInstanceValidator myInstanceVal;
	private FhirValidator myVal;
	private ValidationSupportChain myValidationSupport;

	private MockValidationSupport myMockSupport = new MockValidationSupport(FhirContext.forDstu3Cached());
	@Mock
	private IValidationPolicyAdvisor policyAdvisor;
	@Mock
	private IValidatorResourceFetcher fetcher;

	@SuppressWarnings("unchecked")
	@BeforeEach
	public void before() {
		myVal = ourCtx.newValidator();
		myVal.setValidateAgainstStandardSchema(false);
		myVal.setValidateAgainstStandardSchematron(false);

		myValidationSupport = new ValidationSupportChain(
			myMockSupport,
			ourCtx.getValidationSupport());
		myInstanceVal = new FhirInstanceValidator(myValidationSupport);

		myVal.registerValidatorModule(myInstanceVal);
	}

	private Object defaultString(Integer theLocationLine) {
		return theLocationLine != null ? theLocationLine.toString() : "";
	}

	private <T extends IBaseResource> T loadResource(String theFilename, Class<T> theType) throws IOException {
		return ourCtx.newJsonParser().parseResource(theType, loadResource(theFilename));
	}

	private List<SingleValidationMessage> logResultsAndReturnAll(ValidationResult theOutput) {
		List<SingleValidationMessage> retVal = new ArrayList<SingleValidationMessage>();

		int index = 0;
		for (SingleValidationMessage next : theOutput.getMessages()) {
			ourLog.info("Result {}: {} - {}:{} {} - {}",
				index, next.getSeverity(), defaultString(next.getLocationLine()), defaultString(next.getLocationCol()), next.getLocationString(), next.getMessage());
			index++;

			retVal.add(next);
		}

		return retVal;
	}

	private List<SingleValidationMessage> logResultsAndReturnNonInformationalOnes(ValidationResult theOutput) {
		List<SingleValidationMessage> retVal = new ArrayList<>();

		int index = 0;
		for (SingleValidationMessage next : theOutput.getMessages()) {
			ourLog.info("Result {}: {} - {} - {}", index, next.getSeverity(), next.getLocationString(), next.getMessage());
			index++;

			if (next.getSeverity() != ResultSeverityEnum.INFORMATION) {
				retVal.add(next);
			}
		}

		return retVal;
	}

	@Test
	public void testValidateWithIso3166() throws IOException {
		loadNL();

		FhirValidator val = ourCtx.newValidator();
		val.registerValidatorModule(new FhirInstanceValidator(myValidationSupport));

		// Code in VS
		{
			Patient p = loadResource("/dstu3/nl/nl-core-patient-instance.json", Patient.class);
			ValidationResult result = val.validateWithResult(p);
			List<SingleValidationMessage> all = logResultsAndReturnNonInformationalOnes(result);
			assertTrue(result.isSuccessful());
			assertThat(all).isEmpty();
		}

		// Code not in VS
		{
			Patient p = loadResource("/dstu3/nl/nl-core-patient-instance-invalid-country.json", Patient.class);
			ValidationResult result = val.validateWithResult(p);
			assertFalse(result.isSuccessful());
			List<SingleValidationMessage> all = logResultsAndReturnAll(result);
			assertThat(all).hasSize(2);
			assertEquals(ResultSeverityEnum.ERROR, all.get(0).getSeverity());
			assertEquals("Unknown code 'urn:iso:std:iso:3166#QQ'", all.get(0).getMessage());
		}
	}



	/**
     * See #873
     */
	@Test
	public void testCompareTimesWithDifferentTimezones() {
		Procedure procedure = new Procedure();
		procedure.setStatus(Procedure.ProcedureStatus.COMPLETED);
		procedure.getSubject().setReference("Patient/1");
		procedure.getCode().setText("Some proc");

		Period period = new Period();
		period.setStartElement(new DateTimeType("2000-01-01T00:00:01+05:00"));
		period.setEndElement(new DateTimeType("2000-01-01T00:00:00+04:00"));
		assertThat(period.getStart().getTime()).isLessThan(period.getEnd().getTime());
		procedure.setPerformed(period);

		FhirValidator val = ourCtx.newValidator();
		val.registerValidatorModule(new FhirInstanceValidator(myValidationSupport));

		ValidationResult result = val.validateWithResult(procedure);

		String encoded = ourCtx.newJsonParser().setPrettyPrint(true).encodeResourceToString(result.toOperationOutcome());
		ourLog.info(encoded);

		assertTrue(result.isSuccessful());
	}

	/**
     * See #531
     */
	@Test
	public void testContactPointSystemUrlWorks() {
		Patient p = new Patient();
		ContactPoint t = p.addTelecom();
		t.setSystem(org.hl7.fhir.dstu3.model.ContactPoint.ContactPointSystem.URL);
		t.setValue("http://infoway-inforoute.ca");

		ValidationResult results = myVal.validateWithResult(p);
		List<SingleValidationMessage> outcome = logResultsAndReturnNonInformationalOnes(results);
		assertThat(outcome).isEmpty();

	}

	/**
     * See #703
     */
	@Test
	public void testDstu3UsesLatestDefinitions() throws IOException {
		myMockSupport.addValidConcept("http://www.nlm.nih.gov/research/umls/rxnorm", "316663");
		myMockSupport.addValidConcept("http://snomed.info/sct", "14760008");
		String input = IOUtils.toString(FhirInstanceValidatorDstu3Test.class.getResourceAsStream("/bug703.json"), Charsets.UTF_8);

		ValidationResult results = myVal.validateWithResult(input);
		List<SingleValidationMessage> outcome = logResultsAndReturnNonInformationalOnes(results);
		assertThat(outcome).isEmpty();

	}

	@Test
	public void testValidateQuestionnaire() throws IOException {
		CodeSystem csYesNo = loadResource("/dstu3/fmc01-cs-yesnounk.json", CodeSystem.class);
		myMockSupport.addCodeSystem(csYesNo.getUrl(), csYesNo);
		CodeSystem csBinderRecommended = loadResource("/dstu3/fmc01-cs-binderrecommended.json", CodeSystem.class);
		myMockSupport.addCodeSystem(csBinderRecommended.getUrl(), csBinderRecommended);
		ValueSet vsBinderRequired = loadResource("/dstu3/fmc01-vs-binderrecommended.json", ValueSet.class);
		myMockSupport.addValueSet(vsBinderRequired.getUrl(), vsBinderRequired);
		myMockSupport.addValueSet("ValueSet/" + vsBinderRequired.getIdElement().getIdPart(), vsBinderRequired);
		ValueSet vsYesNo = loadResource("/dstu3/fmc01-vs-yesnounk.json", ValueSet.class);
		myMockSupport.addValueSet(vsYesNo.getUrl(), vsYesNo);
		myMockSupport.addValueSet("ValueSet/" + vsYesNo.getIdElement().getIdPart(), vsYesNo);
		Questionnaire q = loadResource("/dstu3/fmc01-questionnaire.json", Questionnaire.class);
		myMockSupport.addQuestionnaire("Questionnaire/" + q.getIdElement().getIdPart(), q);

		QuestionnaireResponse qr = loadResource("/dstu3/fmc01-questionnaireresponse.json", QuestionnaireResponse.class);
		ValidationResult result = myVal.validateWithResult(qr);
		List<SingleValidationMessage> errors = logResultsAndReturnNonInformationalOnes(result);
		assertThat(errors).isEmpty();

	}

	@Test
	public void testValidateQuestionnaire03() throws IOException {
		CodeSystem csYesNo = loadResource("/dstu3/fmc01-cs-yesnounk.json", CodeSystem.class);
		myMockSupport.addCodeSystem(csYesNo.getUrl(), csYesNo);
		CodeSystem csBinderRecommended = loadResource("/dstu3/fmc03-cs-binderrecommend.json", CodeSystem.class);
		myMockSupport.addCodeSystem(csBinderRecommended.getUrl(), csBinderRecommended);

		ValueSet vsBinderRequired = loadResource("/dstu3/fmc03-vs-binderrecommend.json", ValueSet.class);
		myMockSupport.addValueSet(vsBinderRequired.getUrl(), vsBinderRequired);
		myMockSupport.addValueSet("ValueSet/" + vsBinderRequired.getIdElement().getIdPart(), vsBinderRequired);
		ValueSet vsYesNo = loadResource("/dstu3/fmc03-vs-fmcyesno.json", ValueSet.class);
		myMockSupport.addValueSet(vsYesNo.getUrl(), vsYesNo);
		myMockSupport.addValueSet("ValueSet/" + vsYesNo.getIdElement().getIdPart(), vsYesNo);
		Questionnaire q = loadResource("/dstu3/fmc03-questionnaire.json", Questionnaire.class);
		myMockSupport.addQuestionnaire("Questionnaire/" + q.getIdElement().getIdPart(), q);

		QuestionnaireResponse qr = loadResource("/dstu3/fmc03-questionnaireresponse.json", QuestionnaireResponse.class);
		ValidationResult result = myVal.validateWithResult(qr);
		List<SingleValidationMessage> errors = logResultsAndReturnAll(result);
		assertThat(errors).isEmpty();

	}

	@Test
	public void testValidateQuestionnaireWithEnableWhenAndSubItems_ShouldNotBeEnabled() throws IOException {
		CodeSystem csYesNo = loadResource("/dstu3/fmc01-cs-yesnounk.json", CodeSystem.class);
		myMockSupport.addCodeSystem(csYesNo.getUrl(), csYesNo);
		CodeSystem csBinderRecommended = loadResource("/dstu3/fmc02-cs-binderrecomm.json", CodeSystem.class);
		myMockSupport.addCodeSystem(csBinderRecommended.getUrl(), csBinderRecommended);
		ValueSet vsBinderRequired = loadResource("/dstu3/fmc02-vs-binderrecomm.json", ValueSet.class);
		myMockSupport.addValueSet(vsBinderRequired.getUrl(), vsBinderRequired);
		myMockSupport.addValueSet("ValueSet/" + vsBinderRequired.getIdElement().getIdPart(), vsBinderRequired);
		ValueSet vsYesNo = loadResource("/dstu3/fmc01-vs-yesnounk.json", ValueSet.class);
		myMockSupport.addValueSet(vsYesNo.getUrl(), vsYesNo);
		myMockSupport.addValueSet("ValueSet/" + vsYesNo.getIdElement().getIdPart(), vsYesNo);
		Questionnaire q = loadResource("/dstu3/fmc02-questionnaire.json", Questionnaire.class);
		myMockSupport.addQuestionnaire("Questionnaire/" + q.getIdElement().getIdPart(), q);

		QuestionnaireResponse qr = loadResource("/dstu3/fmc02-questionnaireresponse-01.json", QuestionnaireResponse.class);
		ValidationResult result = myVal.validateWithResult(qr);
		List<SingleValidationMessage> errors = logResultsAndReturnNonInformationalOnes(result);
		assertThat(errors.get(0).getMessage()).contains("Item has answer, even though it is not enabled (item id = 'BO_ConsDrop')");
		assertThat(errors).hasSize(1);
	}

	@Test
	public void testValidateQuestionnaireWithEnableWhenAndSubItems_ShouldBeEnabled() throws IOException {
		CodeSystem csYesNo = loadResource("/dstu3/fmc01-cs-yesnounk.json", CodeSystem.class);
		myMockSupport.addCodeSystem(csYesNo.getUrl(), csYesNo);
		CodeSystem csBinderRecommended = loadResource("/dstu3/fmc02-cs-binderrecomm.json", CodeSystem.class);
		myMockSupport.addCodeSystem(csBinderRecommended.getUrl(), csBinderRecommended);
		ValueSet vsBinderRequired = loadResource("/dstu3/fmc02-vs-binderrecomm.json", ValueSet.class);
		myMockSupport.addValueSet(vsBinderRequired.getUrl(), vsBinderRequired);
		myMockSupport.addValueSet("ValueSet/" + vsBinderRequired.getIdElement().getIdPart(), vsBinderRequired);
		ValueSet vsYesNo = loadResource("/dstu3/fmc01-vs-yesnounk.json", ValueSet.class);
		myMockSupport.addValueSet(vsYesNo.getUrl(), vsYesNo);
		myMockSupport.addValueSet("ValueSet/" + vsYesNo.getIdElement().getIdPart(), vsYesNo);
		Questionnaire q = loadResource("/dstu3/fmc02-questionnaire.json", Questionnaire.class);
		myMockSupport.addQuestionnaire("Questionnaire/" + q.getIdElement().getIdPart(), q);

		QuestionnaireResponse qr = loadResource("/dstu3/fmc02-questionnaireresponse-02.json", QuestionnaireResponse.class);
		ValidationResult result = myVal.validateWithResult(qr);
		List<SingleValidationMessage> errors = logResultsAndReturnNonInformationalOnes(result);
		assertThat(errors).isEmpty();
	}

	/**
     * See #872
     */
	@Test
	public void testExtensionUrlWithHl7Url() throws IOException {
		String input = IOUtils.toString(FhirInstanceValidatorDstu3Test.class.getResourceAsStream("/bug872-ext-with-hl7-url.json"), Charsets.UTF_8);
		ValidationResult output = myVal.validateWithResult(input);
		List<SingleValidationMessage> nonInfo = logResultsAndReturnNonInformationalOnes(output);
		assertThat(nonInfo).isEmpty();
	}

	@Test
	public void testGoal() {
		myMockSupport.addValidConcept("http://foo", "some other goal");
		Goal goal = new Goal();
		goal.setSubject(new Reference("Patient/123"));
		goal.setDescription(new CodeableConcept().addCoding(new Coding("http://foo", "some other goal", "")));
		goal.setStatus(Goal.GoalStatus.INPROGRESS);

		ValidationResult results = myVal.validateWithResult(goal);
		List<SingleValidationMessage> outcome = logResultsAndReturnNonInformationalOnes(results);
		assertThat(outcome).isEmpty();
	}

	/**
     * An invalid local reference should not cause a ServiceException.
     */
	@Test
	public void testInvalidLocalReference() {
		Questionnaire resource = new Questionnaire();
		resource.setStatus(PublicationStatus.ACTIVE);

		QuestionnaireItemComponent item = new QuestionnaireItemComponent();
		item.setLinkId("linkId-1");
		item.setType(QuestionnaireItemType.CHOICE);
		item.setOptions(new Reference("#invalid-ref"));
		resource.addItem(item);

		ValidationResult output = myVal.validateWithResult(resource);
		List<SingleValidationMessage> nonInfo = logResultsAndReturnNonInformationalOnes(output);
		assertThat(nonInfo).hasSize(2);
	}

	@Test
	public void testIsNoTerminologyChecks() {
		assertFalse(myInstanceVal.isNoTerminologyChecks());
		myInstanceVal.setNoTerminologyChecks(true);
		assertTrue(myInstanceVal.isNoTerminologyChecks());
	}

	/**
     * See #824
     */
	@Test
	public void testValidateBadCodeForRequiredBinding() throws IOException {
		StructureDefinition fiphrPefStu3 = ourCtx.newJsonParser().parseResource(StructureDefinition.class, loadResource("/dstu3/bug824-profile-fiphr-pef-stu3.json"));
		myMockSupport.addStructureDefinition("http://phr.kanta.fi/StructureDefinition/fiphr-pef-stu3", fiphrPefStu3);

		StructureDefinition fiphrDevice = ourCtx.newJsonParser().parseResource(StructureDefinition.class, loadResource("/dstu3/bug824-fiphr-device.json"));
		myMockSupport.addStructureDefinition("http://phr.kanta.fi/StructureDefinition/fiphr-device", fiphrDevice);

		StructureDefinition fiphrCreatingApplication = ourCtx.newJsonParser().parseResource(StructureDefinition.class, loadResource("/dstu3/bug824-creatingapplication.json"));
		myMockSupport.addStructureDefinition("http://phr.kanta.fi/StructureDefinition/fiphr-ext-creatingapplication", fiphrCreatingApplication);

		StructureDefinition fiphrBoolean = ourCtx.newJsonParser().parseResource(StructureDefinition.class, loadResource("/dstu3/bug824-fiphr-boolean.json"));
		myMockSupport.addStructureDefinition("http://phr.kanta.fi/StructureDefinition/fiphr-boolean", fiphrBoolean);

		StructureDefinition medContext = ourCtx.newJsonParser().parseResource(StructureDefinition.class, loadResource("/dstu3/bug824-fiphr-medicationcontext.json"));
		myMockSupport.addStructureDefinition("http://phr.kanta.fi/StructureDefinition/fiphr-medicationcontext", medContext);

		StructureDefinition fiphrVitalSigns = ourCtx.newJsonParser().parseResource(StructureDefinition.class, loadResource("/dstu3/bug824-fiphr-vitalsigns-stu3.json"));
		myMockSupport.addStructureDefinition("http://phr.kanta.fi/StructureDefinition/fiphr-vitalsigns-stu3", fiphrVitalSigns);

		CodeSystem csObservationMethod = ourCtx.newJsonParser().parseResource(CodeSystem.class, loadResource("/dstu3/bug824-fhirphr-cs-observationmethod.json"));
		myMockSupport.addCodeSystem("http://phr.kanta.fi/fiphr-cs-observationmethod", csObservationMethod);

		ValueSet vsObservationMethod = ourCtx.newJsonParser().parseResource(ValueSet.class, loadResource("/dstu3/bug824-vs-observaionmethod.json"));
		myMockSupport.addValueSet("http://phr.kanta.fi/ValueSet/fiphr-vs-observationmethod", vsObservationMethod);

		ValueSet vsVitalSigns = ourCtx.newJsonParser().parseResource(ValueSet.class, loadResource("/dstu3/bug824-vs-vitalsigns.json"));
		myMockSupport.addValueSet("http://phr.kanta.fi/ValueSet/fiphr-vs-vitalsigns", vsVitalSigns);

		ValueSet vsMedicationContext = ourCtx.newJsonParser().parseResource(ValueSet.class, loadResource("/dstu3/bug824-vs-medicationcontext.json"));
		myMockSupport.addValueSet("http://phr.kanta.fi/ValueSet/fiphr-vs-medicationcontext", vsMedicationContext);

		ValueSet vsConfidentiality = ourCtx.newJsonParser().parseResource(ValueSet.class, loadResource("/dstu3/bug824-vs-confidentiality.json"));
		myMockSupport.addValueSet("http://phr.kanta.fi/ValueSet/fiphr-vs-confidentiality", vsConfidentiality);

		CodeSystem csMedicationContext = ourCtx.newJsonParser().parseResource(CodeSystem.class, loadResource("/dstu3/bug824-fhirphr-cs-medicationcontext.json"));
		myMockSupport.addCodeSystem("http://phr.kanta.fi/fiphr-cs-medicationcontext", csMedicationContext);

		String input = loadResource("/dstu3/bug824-resource.json");
		ValidationResult output = myVal.validateWithResult(input);
		List<SingleValidationMessage> issues = logResultsAndReturnNonInformationalOnes(output);

		assertThat(issues.stream().map(SingleValidationMessage::getMessage).collect(Collectors.toList()).toString()).contains("None of the codings provided are in the value set 'Value Set Finnish PHR Medication Context'");
	}

	@Test
	public void testValidateBigRawJsonResource() throws Exception {
		InputStream stream = FhirInstanceValidatorDstu3Test.class.getResourceAsStream("/conformance.json.gz");
		stream = new GZIPInputStream(stream);
		String input = IOUtils.toString(stream);

		long start = System.currentTimeMillis();
		ValidationResult output = null;
		int passes = 1;
		for (int i = 0; i < passes; i++) {
			ourLog.info("Pass {}", i + 1);
			output = myVal.validateWithResult(input);
		}

		long delay = System.currentTimeMillis() - start;
		long per = delay / passes;

		logResultsAndReturnAll(output);

		ourLog.info("Took {} ms -- {}ms / pass", delay, per);
	}

	@Test
	// @Disabled
	public void testValidateBuiltInProfiles() throws Exception {
		org.hl7.fhir.dstu3.model.Bundle bundle;
		String name = "profiles-resources";
		ourLog.info("Uploading " + name);
		String vsContents;
		vsContents = ClasspathUtil.loadResource("/org/hl7/fhir/dstu3/model/profile/" + name + ".xml");

		TreeSet<String> ids = new TreeSet<String>();

		bundle = ourCtx.newXmlParser().parseResource(org.hl7.fhir.dstu3.model.Bundle.class, vsContents);
		for (BundleEntryComponent i : bundle.getEntry()) {
			org.hl7.fhir.dstu3.model.Resource next = i.getResource();
			ids.add(next.getId());

			if (next instanceof StructureDefinition) {
				StructureDefinition sd = (StructureDefinition) next;
				if (sd.getKind() == StructureDefinitionKind.LOGICAL) {
					ourLog.info("Skipping logical type: {}", next.getId());
					continue;
				}
				if (sd.getUrl().equals("http://hl7.org/fhir/StructureDefinition/Resource")) {
					continue;
				}
			}

			ourLog.info("Validating {}", next.getId());
			String reEncoded = ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(next);

			ValidationResult output = myVal.validateWithResult(reEncoded);
			List<SingleValidationMessage> errors = logResultsAndReturnNonInformationalOnes(output);

			errors = errors
				.stream()
				.filter(t -> {
					if (t.getLocationString().contains("example")) {
						ourLog.warn("Ignoring error in example path: {}", t);
						return false;
					} else if (t.getMessage().contains("ValueSet as a URI SHALL start with http:// or https:// or urn:")) {
						// Some DSTU3 structures have missing binding information
						return false;
					} else if (t.getMessage().contains("The Unicode sequence has unterminated bi-di control characters")) {
						// Some DSTU3 structures contain bi-di control characters, and a check for this was added recently.
						return false;
					} else if (t.getMessage().contains("The markdown contains content that appears to be an embedded")) {
						// Some DSTU3 structures contain URLs with <> around them
						return false;
					} else if (t.getMessage().startsWith("value should not start or finish with whitespace") && t.getMessage().endsWith("\\u00a0'")) {
						// Some DSTU3 messages end with a unicode Non-breaking space character
						return false;
					}  else if (t.getMessage().contains("Found # expecting a token name")) {
						// Some DSTU3 messages contain incomplete encoding for single quotes (#39 vs &#39)
						return false;
					} else if (t.getMessage().contains("sdf-15") && t.getMessage().contains("The name 'kind' is not valid for any of the possible types")) {
						// Find constraint sdf-15 fails with stricter core validation.
						return false;
					}
					else if (t.getMessage().contains("bdl-7") && t.getMessage().contains("Error evaluating FHIRPath expression: The parameter type http://hl7.org/fhir/StructureDefinition/uri is not legal for where parameter 1. expecting SINGLETON[http://hl7.org/fhirpath/System.Boolean]")) {
						// Find constraint sdf-15 fails with stricter core validation.
						return false;
					}
					else if (t.getMessage().contains("side is inherently a collection") && t.getMessage().endsWith("may fail or return false if there is more than one item in the content being evaluated")) {
						// Some DSTU3 FHIRPath expressions now produce warnings if a singleton is compared to a collection that potentially has > 1 elements
						return false;
					} else if (t.getMessage().contains("When HL7 is publishing a resource, the owning committee must be stated using the http://hl7.org/fhir/StructureDefinition/structuredefinition-wg extension")) {
						// DSTU3 resources predate this strict requirement
						return false;
					} else if (t.getMessage().equals("The nominated WG 'rcrim' is unknown")) {
						//The rcrim workgroup is now brr http://www.hl7.org/Special/committees/rcrim/index.cfm
						return false;
					} else if (t.getMessage().contains("which is experimental, but this structure is not labeled as experimental")
						//DSTU3 resources will not pass validation with this new business rule (2024-09-17) https://github.com/hapifhir/org.hl7.fhir.core/commit/7d05d38509895ddf8614b35ffb51b1f5363f394c
					) {
						return false;
					} else if(t.getMessage().contains("The constraint key 'inv-1' already exists at the location 'http://hl7.org/fhir/StructureDefinition/TestScript' with a different expression")) {
						return false;
					} else if(t.getMessage().contains("The element slicing is prohibited on the element DomainResource.extension") || t.getMessage().contains("The element slicing is prohibited on the element DomainResource.modifierExtension")) {
						// Core 6.5.15 contains this new validation that let the test fail
						return false;
					}
					else if (t.getSeverity() == ResultSeverityEnum.WARNING
						&& ( "VALIDATION_HL7_PUBLISHER_MISMATCH".equals(t.getMessageId())
						|| "VALIDATION_HL7_PUBLISHER_MISMATCH2".equals(t.getMessageId())
						|| "VALIDATION_HL7_WG_URL".equals(t.getMessageId())
					)) {
						// Workgroups have been updated and have slightly different naming conventions and URLs.
						return false;
					}
					else {
						return true;
					}
				})
				.collect(Collectors.toList());

			if (errors.size() > 0) {
				StringBuilder b = new StringBuilder();
				int line = 0;
				for (String nextLine : reEncoded.split("\n")) {
					b.append(line++).append(": ").append(nextLine).append("\n");
				}
				ourLog.info("Failing validation:\n{}", b);
			}

			assertThat(errors).as("Failed to validate " + i.getFullUrl() + " - " + errors).isEmpty();
		}

		ourLog.info("Validated the following:\n{}", ids);
	}

	@Test
	public void testValidateBundleWithNoType() throws Exception {
		String vsContents = ClasspathUtil.loadResource("/dstu3/bundle-with-no-type.json");

		ValidationResult output = myVal.validateWithResult(vsContents);
		logResultsAndReturnNonInformationalOnes(output);
		assertThat(output.getMessages().toString()).contains("Bundle.type: minimum required = 1, but only found 0");
	}

	@Test
	@Disabled
	public void testValidateBundleWithObservations() throws Exception {
		String name = "profiles-resources";
		ourLog.info("Uploading " + name);
		String inputString;
		inputString = ClasspathUtil.loadResource("/brian_reinhold_bundle.json");
		Bundle bundle = ourCtx.newJsonParser().parseResource(Bundle.class, inputString);

		FHIRPathEngine fp = new FHIRPathEngine(new HapiWorkerContext(ourCtx, myValidationSupport));
		List<Base> fpOutput;
		BooleanType bool;

		fpOutput = fp.evaluate(bundle.getEntry().get(0).getResource(), "component.where(code = %resource.code).empty()");
		assertThat(fpOutput).hasSize(1);
		bool = (BooleanType) fpOutput.get(0);
		assertTrue(bool.getValue());
		//
		// fpOutput = fp.evaluate(bundle, "component.where(code = %resource.code).empty()");
		// assertEquals(1, fpOutput.size());
		// bool = (BooleanType) fpOutput.get(0);
		// assertTrue(bool.getValue());

		ValidationResult output = myVal.validateWithResult(inputString);
		List<SingleValidationMessage> errors = logResultsAndReturnNonInformationalOnes(output);
		assertThat(errors).isEmpty();

	}

	/**
     * See #851
     */
	@Test
	public void testValidateCoding() {
		ImagingStudy is = new ImagingStudy();
		is.setUid("urn:oid:1.2.3.4");
		is.getPatient().setReference("Patient/1");

		is.getModalityListFirstRep().setSystem("http://dicom.nema.org/resources/ontology/DCM");
		is.getModalityListFirstRep().setCode("BAR");
		is.getModalityListFirstRep().setDisplay("Hello");

		ValidationResult results = myVal.validateWithResult(is);
		List<SingleValidationMessage> outcome = logResultsAndReturnNonInformationalOnes(results);
		assertThat(outcome).hasSize(1);
		assertThat(outcome.get(0).getMessage()).startsWith("The Coding provided (http://dicom.nema.org/resources/ontology/DCM#BAR) was not found in the value set 'Acquisition Modality Codes' (http://hl7.org/fhir/ValueSet/dicom-cid29|20121129)");
	}

	/**
     * FHIRPathEngine was throwing Error...
     */
	@Test
	public void testValidateCrucibleCarePlan() throws Exception {
		org.hl7.fhir.dstu3.model.Bundle bundle;
		String name = "profiles-resources";
		ourLog.info("Uploading " + name);
		String vsContents;
		vsContents = ClasspathUtil.loadResource("/crucible-condition.xml");

		ValidationResult output = myVal.validateWithResult(vsContents);
		List<SingleValidationMessage> errors = logResultsAndReturnNonInformationalOnes(output);
	}

	@Test
	public void testValidateDocument() throws Exception {
		String vsContents = ClasspathUtil.loadResource("/sample-document.xml");

		ValueSet valuesetDocTypeCodes = loadResource("/dstu3/valueset-doc-typecodes.json", ValueSet.class);
		myMockSupport.addValueSet(valuesetDocTypeCodes.getUrl(), valuesetDocTypeCodes);
		myMockSupport.addValueSet("ValueSet/" + valuesetDocTypeCodes.getIdElement().getIdPart(), valuesetDocTypeCodes);

		ValueSet valuesetV2_0131 = loadResource("/dstu3/valueset-v2-0131.json", ValueSet.class);
		myMockSupport.addValueSet(valuesetV2_0131.getUrl(), valuesetV2_0131);
		myMockSupport.addValueSet("ValueSet/" + valuesetV2_0131.getIdElement().getIdPart(), valuesetV2_0131);

		org.hl7.fhir.dstu3.model.CodeSystem mockOfRoleCode = new org.hl7.fhir.dstu3.model.CodeSystem();
		mockOfRoleCode.setUrl("http://hl7.org/fhir/v3/RoleCode");
		mockOfRoleCode.setContent(org.hl7.fhir.dstu3.model.CodeSystem.CodeSystemContentMode.COMPLETE);
		mockOfRoleCode.addConcept().setCode("PRN");
		mockOfRoleCode.addConcept().setCode("GPARNT");
		myMockSupport.addCodeSystem("http://hl7.org/fhir/v3/RoleCode", mockOfRoleCode);

		myMockSupport.addValidConcept("http://hl7.org/fhir/v3/RoleCode", "GPARNT");
		myMockSupport.addValidConcept("http://hl7.org/fhir/v3/RoleCode", "PRN");


		org.hl7.fhir.dstu3.model.CodeSystem mockOfLoinc = new org.hl7.fhir.dstu3.model.CodeSystem();
		mockOfLoinc.setUrl("http://hl7.org/fhir/uv/ips/CodeSystem/absent-unknown-uv-ips");
		mockOfLoinc.setContent(org.hl7.fhir.dstu3.model.CodeSystem.CodeSystemContentMode.COMPLETE);

		String[] validLoincCodes = {
			"29299-5",
			"18776-5",
			"69730-0",
			"8716-3",
			"10160-0",
			"29549-3",
			"11450-4",
			"48765-2",
			"30954-2",
			"47519-4",
			"11369-6",
			"29762-2",
			"46240-8",
			"42348-3",
			"42348-3",
			"34133-9"
		};

		for (String validLoincCode: validLoincCodes) {
			myMockSupport.addValidConcept("http://loinc.org", validLoincCode);
			mockOfLoinc.addConcept().setCode(validLoincCode);
		}

		myMockSupport.addCodeSystem("http://loinc.org", mockOfLoinc);

		ValidationResult output = myVal.validateWithResult(vsContents);
		logResultsAndReturnNonInformationalOnes(output);
		assertTrue(output.isSuccessful());
	}


	@Test
	public void testValidateUsingDifferentialProfile() throws IOException {
		loadNL();

		Patient resource = loadResource("/dstu3/nl/nl-core-patient-01.json", Patient.class);
		ValidationResult results = myVal.validateWithResult(resource);
		List<SingleValidationMessage> outcome = logResultsAndReturnNonInformationalOnes(results);
		assertThat(outcome.toString()).contains("The Coding provided (urn:oid:2.16.840.1.113883.2.4.4.16.34#6030) was not found in the value set 'LandGBACodelijst'");
	}

	private void loadNL() throws IOException {
		loadValueSet("/dstu3/nl/2.16.840.1.113883.2.4.3.11.60.40.2.20.5.1--20171231000000.json");
		loadValueSet("/dstu3/nl/LandGBACodelijst-2.16.840.1.113883.2.4.3.11.60.40.2.20.5.1--20171231000000.json");
		loadValueSet("/dstu3/nl/LandISOCodelijst-2.16.840.1.113883.2.4.3.11.60.40.2.20.5.2--20171231000000.json");

		loadStructureDefinition("/dstu3/nl/extension-code-specification.json");
		loadStructureDefinition("/dstu3/nl/nl-core-patient.json");
		loadStructureDefinition("/dstu3/nl/proficiency.json");
		loadStructureDefinition("/dstu3/nl/zibpatientlegalstatus.json");
		loadStructureDefinition("/dstu3/nl/nl-core-contactpoint.json");
		loadStructureDefinition("/dstu3/nl/Zibextensioncodespecification.json");
		loadStructureDefinition("/dstu3/nl/nl-core-humanname.json");
		loadStructureDefinition("/dstu3/nl/nl-core-preferred-pharmacy.json");
		loadStructureDefinition("/dstu3/nl/nl-core-address.json");
		loadStructureDefinition("/dstu3/nl/nl-core-address-official.json");
		loadStructureDefinition("/dstu3/nl/Comment.json");
		loadStructureDefinition("/dstu3/nl/nl-core-careplan.json");
		loadStructureDefinition("/dstu3/nl/nl-core-healthcareservice.json");
		loadStructureDefinition("/dstu3/nl/nl-core-organization.json");
		loadStructureDefinition("/dstu3/nl/nl-core-practitionerrole.json");
		loadStructureDefinition("/dstu3/nl/nl-core-careteam.json");
		loadStructureDefinition("/dstu3/nl/nl-core-location.json");
		loadStructureDefinition("/dstu3/nl/nl-core-person.json");
		loadStructureDefinition("/dstu3/nl/nl-core-relatedperson.json");
		loadStructureDefinition("/dstu3/nl/nl-core-episodeofcare.json");
		loadStructureDefinition("/dstu3/nl/nl-core-observation.json");
		loadStructureDefinition("/dstu3/nl/nl-core-practitioner.json");
		loadStructureDefinition("/dstu3/nl/nl-core-relatedperson-role.json");
		loadStructureDefinition("/dstu3/nl/PractitionerRoleReference.json");
	}

	public void loadValueSet(String theFilename) throws IOException {
		ValueSet vs = loadResource(theFilename, ValueSet.class);
		myMockSupport.addValueSet(vs.getUrl(), vs);
	}

	public void loadStructureDefinition(String theFilename) throws IOException {
		StructureDefinition sd = loadResource(theFilename, StructureDefinition.class);
		myMockSupport.addStructureDefinition(sd.getUrl(), sd);
	}


	/**
     * See #739
     */
	@Test
	public void testValidateMedicationIngredient() throws IOException {
		String input = IOUtils.toString(FhirInstanceValidatorDstu3Test.class.getResourceAsStream("/dstu3/bug739.json"), Charsets.UTF_8);

		ValidationResult results = myVal.validateWithResult(input);
		List<SingleValidationMessage> outcome = logResultsAndReturnNonInformationalOnes(results);
		assertThat(outcome.toString()).contains("Medication.ingredient.item[x]: minimum required = 1, but only found 0");

	}

	@Test
	public void testValidateRawJsonResource() {
		//@formatter:off
		String input = "{" + "\"resourceType\":\"Patient\"," + "\"id\":\"123\"" + "}";
		//@formatter:on

		ValidationResult output = myVal.validateWithResult(input);
		assertThat(output.getMessages().size()).as(output.toString()).isEqualTo(0);
	}

	@Test
	public void testValidateRawJsonResourceBadAttributes() {
		//@formatter:off
		String input =
			"{" +
				"\"resourceType\":\"Patient\"," +
				"\"id\":\"123\"," +
				"\"foo\":\"123\"" +
				"}";
		//@formatter:on

		ValidationResult output = myVal.validateWithResult(input);
		assertThat(output.getMessages().size()).as(output.toString()).isEqualTo(1);
		ourLog.info(output.getMessages().get(0).getLocationString());
		ourLog.info(output.getMessages().get(0).getMessage());
		assertEquals("Patient", output.getMessages().get(0).getLocationString());
		assertEquals("Unrecognized property 'foo'", output.getMessages().get(0).getMessage());
	}

	@Test
	public void testValidateRawJsonResourceFromExamples() throws Exception {
		// @formatter:off
		String input = IOUtils.toString(FhirInstanceValidator.class.getResourceAsStream("/testscript-search.json"));
		// @formatter:on

		ValidationResult output = myVal.validateWithResult(input);
		logResultsAndReturnNonInformationalOnes(output);
		// assertEquals(output.toString(), 1, output.getMessages().size());
		// ourLog.info(output.getMessages().get(0).getLocationString());
		// ourLog.info(output.getMessages().get(0).getMessage());
		// assertEquals("/foo", output.getMessages().get(0).getLocationString());
		// assertEquals("Element is unknown or does not match any slice", output.getMessages().get(0).getMessage());
	}

	@Test
	public void testValidateRawJsonResourceWithUnknownExtension() {

		Patient patient = new Patient();
		patient.setId("1");

		Extension ext = patient.addExtension();
		ext.setUrl("http://hl7.org/fhir/v3/ethnicity");
		ext.setValue(new CodeType("Hispanic or Latino"));

		String encoded = ourCtx.newJsonParser().setPrettyPrint(true).encodeResourceToString(patient);
		ourLog.info(encoded);

		/*
		 * {
		 * "resourceType": "Patient",
		 * "id": "1",
		 * "extension": [
		 * {
		 * "url": "http://hl7.org/fhir/v3/ethnicity",
		 * "valueCode": "Hispanic or Latino"
		 * }
		 * ]
		 * }
		 */

		ValidationResult output = myVal.validateWithResult(encoded);
		assertThat(output.getMessages().size()).as(output.toString()).isEqualTo(1);

		assertEquals("Unknown extension http://hl7.org/fhir/v3/ethnicity", output.getMessages().get(0).getMessage());
		assertEquals(ResultSeverityEnum.INFORMATION, output.getMessages().get(0).getSeverity());
	}

	@Test
	public void testValidateRawJsonResourceWithUnknownExtensionNotAllowed() {

		Patient patient = new Patient();
		patient.setId("1");

		Extension ext = patient.addExtension();
		ext.setUrl("http://hl7.org/fhir/v3/ethnicity");
		ext.setValue(new CodeType("Hispanic or Latino"));

		String encoded = ourCtx.newJsonParser().setPrettyPrint(true).encodeResourceToString(patient);
		ourLog.info(encoded);

		/*
		 * {
		 * "resourceType": "Patient",
		 * "id": "1",
		 * "extension": [
		 * {
		 * "url": "http://hl7.org/fhir/v3/ethnicity",
		 * "valueCode": "Hispanic or Latino"
		 * }
		 * ]
		 * }
		 */

		myInstanceVal.setAnyExtensionsAllowed(false);
		ValidationResult output = myVal.validateWithResult(encoded);
		assertThat(output.getMessages().size()).as(output.toString()).isEqualTo(1);

		assertEquals("The extension http://hl7.org/fhir/v3/ethnicity could not be found so is not allowed here", output.getMessages().get(0).getMessage());
		assertEquals(ResultSeverityEnum.ERROR, output.getMessages().get(0).getSeverity());
	}

	@Test
	public void testValidateRawXmlResource() {
		// @formatter:off
		String input = "<Patient xmlns=\"http://hl7.org/fhir\">" + "<id value=\"123\"/>" + "</Patient>";
		// @formatter:on

		ValidationResult output = myVal.validateWithResult(input);
		assertThat(output.getMessages().size()).as(output.toString()).isEqualTo(0);
	}

	@Test
	public void testValidateRawXmlResourceBadAttributes() {
		//@formatter:off
		String input = "<Patient xmlns=\"http://hl7.org/fhir\">" + "<id value=\"123\"/>" + "<foo value=\"222\"/>"
			+ "</Patient>";
		//@formatter:on

		ValidationResult output = myVal.validateWithResult(input);
		assertThat(output.getMessages().size()).as(output.toString()).isEqualTo(1);
		ourLog.info(output.getMessages().get(0).getLocationString());
		ourLog.info(output.getMessages().get(0).getMessage());
		assertEquals("/f:Patient", output.getMessages().get(0).getLocationString());
		assertEquals("Undefined element 'foo' at /f:Patient", output.getMessages().get(0).getMessage());
	}

	@Test
	public void testValidateRawXmlResourceWithEmptyPrimitive() {
		String input = "<Patient xmlns=\"http://hl7.org/fhir\"><name><given/></name></Patient>";

		ValidationResult output = myVal.validateWithResult(input);
		ourLog.debug(ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(output.toOperationOutcome()));
		assertThat(output.getMessages().size()).as(output.toString()).isEqualTo(3);
		assertThat(output.getMessages().get(0).getMessage()).contains("Element must have some content");
		assertThat(output.getMessages().get(1).getMessage()).contains("Primitive types must have a value or must have child extensions");
	}

	@Test
	public void testValidateRawXmlResourceWithPrimitiveContainingOnlyAnExtension() {
		// @formatter:off
		String input = "<ActivityDefinition xmlns=\"http://hl7.org/fhir\">\n" +
			"                        <id value=\"referralToMentalHealthCare\"/>\n" +
			"                        <status value=\"draft\"/>\n" +
			"                        <description value=\"refer to primary care mental-health integrated care program for evaluation and treatment of mental health conditions now\"/>\n" +
			"                        <code>\n" +
			"                                <coding>\n" +
			"                                        <!-- Error: Connection to http://localhost:960 refused -->\n" +
			"                                        <!--<system value=\"http://snomed.info/sct\"/>-->\n" +
			"                                        <code value=\"306206005\"/>\n" +
			"                                </coding>\n" +
			"                        </code>\n" +
			"                        <!-- Specifying this this way results in a null reference exception in the validator -->\n" +
			"                        <timingTiming>\n" +
			"                                <event>\n" +
			"                                        <extension url=\"http://fhir.org/cql-expression\">\n" +
			"                                                <valueString value=\"Now()\"/>\n" +
			"                                        </extension>\n" +
			"                                </event>\n" +
			"                        </timingTiming>\n" +
			"                </ActivityDefinition>";
		// @formatter:on

		ValidationResult output = myVal.validateWithResult(input);
		List<SingleValidationMessage> res = logResultsAndReturnNonInformationalOnes(output);
		assertThat(res.size()).as(output.toString()).isEqualTo(1);
		assertEquals("Coding has no system. A code with no system has no defined meaning, and it cannot be validated. A system should be provided", output.getMessages().get(0).getMessage());
	}

	/**
	 * A reference with only an identifier should be valid
	 */
	@Test
	public void testValidateReferenceWithDisplayValid() {
		Patient p = new Patient();
		p.getManagingOrganization().setDisplay("HELLO");

		ValidationResult output = myVal.validateWithResult(p);
		List<SingleValidationMessage> nonInfo = logResultsAndReturnNonInformationalOnes(output);
		assertThat(nonInfo).isEmpty();
	}

	/**
	 * A reference with only an identifier should be valid
	 */
	@Test
	public void testValidateReferenceWithIdentifierValid() {
		Patient p = new Patient();
		p.getManagingOrganization().getIdentifier().setSystem("http://acme.org");
		p.getManagingOrganization().getIdentifier().setValue("foo");

		ValidationResult output = myVal.validateWithResult(p);
		List<SingleValidationMessage> nonInfo = logResultsAndReturnNonInformationalOnes(output);
		assertThat(nonInfo).isEmpty();
	}

	/**
	 * See #370
	 */
	@Test
	public void testValidateRelatedPerson() {

		/*
		 * Try with a code that is in http://hl7.org/fhir/ValueSet/relatedperson-relationshiptype
		 * and therefore should validate
		 */
		RelatedPerson rp = new RelatedPerson();
		rp.getPatient().setReference("Patient/1");
		rp.getRelationship().addCoding().setSystem("http://hl7.org/fhir/v2/0131").setCode("c");

		ValidationResult results = myVal.validateWithResult(rp);
		List<SingleValidationMessage> outcome = logResultsAndReturnNonInformationalOnes(results);
		assertThat(outcome).isEmpty();

		/*
		 * Code system is case insensitive, so try with capital C
		 */
		rp = new RelatedPerson();
		rp.getPatient().setReference("Patient/1");
		rp.getRelationship().addCoding().setSystem("http://hl7.org/fhir/v2/0131").setCode("C");

		results = myVal.validateWithResult(rp);
		outcome = logResultsAndReturnNonInformationalOnes(results);
		assertThat(outcome).isEmpty();

		/*
		 * Now a bad code
		 */
		rp = new RelatedPerson();
		rp.getPatient().setReference("Patient/1");
		rp.getRelationship().addCoding().setSystem("http://hl7.org/fhir/v2/0131").setCode("GAGAGAGA");

		results = myVal.validateWithResult(rp);
		outcome = logResultsAndReturnNonInformationalOnes(results);
		assertThat(outcome).isNotEmpty();

	}

	// TODO: uncomment value false when https://github.com/hapifhir/org.hl7.fhir.core/issues/1766 is fixed
	@ParameterizedTest
	@ValueSource(booleans = {true, /*false*/})
	public void testValidateResourceContainingLoincCode(boolean theShouldSystemReturnIssuesForInvalidCode) {
		myMockSupport.addValidConcept("http://loinc.org", "1234567", theShouldSystemReturnIssuesForInvalidCode);

		Observation input = new Observation();
		// input.getMeta().addProfile("http://hl7.org/fhir/StructureDefinition/devicemetricobservation");

		input.addIdentifier().setSystem("http://acme").setValue("12345");
		input.getContext().setReference("http://foo.com/Encounter/9");
		input.setStatus(ObservationStatus.FINAL);
		input.getCode().addCoding().setSystem("http://loinc.org").setCode("12345");

		myInstanceVal.setValidationSupport(myValidationSupport);
		ValidationResult output = myVal.validateWithResult(input);
		List<SingleValidationMessage> errors = logResultsAndReturnAll(output);

		assertEquals(ResultSeverityEnum.ERROR, errors.get(0).getSeverity());
		assertEquals("Unknown code (for 'http://loinc.org#12345')", errors.get(0).getMessage());
	}

	@Test
	public void testValidateResourceContainingProfileDeclaration() {
		myMockSupport.addValidConcept("http://loinc.org", "12345");

		Observation input = new Observation();
		input.getMeta().addProfile("http://hl7.org/fhir/StructureDefinition/devicemetricobservation");

		input.addIdentifier().setSystem("http://acme").setValue("12345");
		input.getContext().setReference("http://foo.com/Encounter/9");
		input.setStatus(ObservationStatus.FINAL);
		input.getCode().addCoding().setSystem("http://loinc.org").setCode("12345");

		myInstanceVal.setValidationSupport(myValidationSupport);
		ValidationResult output = myVal.validateWithResult(input);
		List<SingleValidationMessage> errors = logResultsAndReturnNonInformationalOnes(output);

		assertThat(errors.toString()).contains("Observation.subject: minimum required = 1, but only found 0");
		assertThat(errors.toString()).contains("Observation.context: max allowed = 0, but found 1");
		assertThat(errors.toString()).contains("Observation.device: minimum required = 1, but only found 0");
	}

	@Test
	public void testValidateResourceContainingProfileDeclarationDoesntResolve() {
		myMockSupport.addValidConcept("http://loinc.org", "12345");

		Observation input = createObservationWithDefaultSubjectPerfomerEffective();
		input.getMeta().addProfile("http://foo/structuredefinition/myprofile");

		input.getCode().addCoding().setSystem("http://loinc.org").setCode("12345");
		input.setStatus(ObservationStatus.FINAL);

		myInstanceVal.setValidationSupport(myValidationSupport);
		ValidationResult output = myVal.validateWithResult(input);
		List<SingleValidationMessage> errors = logResultsAndReturnNonInformationalOnes(output);
		assertThat(errors.toString()).contains("Profile reference 'http://foo/structuredefinition/myprofile' has not been checked because it could not be found");
	}

	@Test
	public void testValidateResourceFailingInvariant() {
		Observation input = new Observation();

		// Has a value, but not a status (which is required)
		input.getCode().addCoding().setSystem("http://loinc.org").setCode("12345");
		input.setValue(new StringType("AAA"));

		ValidationResult output = myVal.validateWithResult(input);
		assertThat(output.getMessages().size()).isGreaterThan(0);
		assertEquals("Observation.status: minimum required = 1, but only found 0 (from http://hl7.org/fhir/StructureDefinition/Observation)", output.getMessages().get(0).getMessage());

	}

	private Observation createObservationWithDefaultSubjectPerfomerEffective() {
		Observation observation = new Observation();
		observation.setSubject(new Reference("Patient/123"));
		observation.addPerformer(new Reference("Practitioner/124"));
		observation.setEffective(new DateTimeType("2023-01-01T11:22:33Z"));
		return observation;
	}

	@Test
	public void testValidateResourceWithDefaultValueset() {
		Observation input = createObservationWithDefaultSubjectPerfomerEffective();

		input.setStatus(ObservationStatus.FINAL);
		input.getCode().setText("No code here!");

		ourLog.debug(ourCtx.newXmlParser().setPrettyPrint(true).encodeResourceToString(input));

		ValidationResult output = myVal.validateWithResult(input);
		assertEquals(output.getMessages().size(), 0);
	}

	@Test
	public void testValidateResourceWithDefaultValuesetBadCode() {

		String input =
			"<Observation xmlns=\"http://hl7.org/fhir\">\n" +
				"   <status value=\"notvalidcode\"/>\n" +
				"   <code>\n" +
				"      <text value=\"No code here!\"/>\n" +
				"   </code>\n" +
				"</Observation>";

		ValidationResult output = myVal.validateWithResult(input);
		logResultsAndReturnAll(output);
		assertThat(output.getMessages().get(0).getMessage()).contains("Unknown code 'http://hl7.org/fhir/observation-status#notvalidcode'");
		assertThat(output.getMessages().get(1).getMessage()).contains("The value provided ('notvalidcode') was not found in the value set 'ObservationStatus'");
	}

	@Test
	public void testValidateResourceWithExampleBindingCodeValidationFailing() {
		myMockSupport.addValidConcept("http://loinc.org", "12345");

		Observation input = createObservationWithDefaultSubjectPerfomerEffective();

		myInstanceVal.setValidationSupport(myValidationSupport);

		input.setStatus(ObservationStatus.FINAL);
		input.getCode().addCoding().setSystem("http://loinc.org").setCode("12345");

		ValidationResult output = myVal.validateWithResult(input);
		List<SingleValidationMessage> errors = logResultsAndReturnNonInformationalOnes(output);
		assertThat(errors.size()).as(errors.toString()).isEqualTo(0);

	}

	@Test
	public void testValidateResourceWithExampleBindingCodeValidationFailingNonLoinc() {
		Observation input = new Observation();

		myInstanceVal.setValidationSupport(myValidationSupport);
		myMockSupport.addValidConcept("http://acme.org", "12345");

		input.setStatus(ObservationStatus.FINAL);
		input.getCode().addCoding().setSystem("http://acme.org").setCode("9988877");

		ValidationResult output = myVal.validateWithResult(input);
		List<SingleValidationMessage> errors = logResultsAndReturnAll(output);
		assertThat(errors.size()).as(errors.toString()).isGreaterThan(0);
		assertEquals("Unknown code (for 'http://acme.org#9988877')", errors.get(0).getMessage());

	}

	@Test
	public void testValidateResourceWithExampleBindingCodeValidationPassingLoinc() {
		Observation input = createObservationWithDefaultSubjectPerfomerEffective();

		myInstanceVal.setValidationSupport(myValidationSupport);
		myMockSupport.addValidConcept("http://loinc.org", "12345");

		input.setStatus(ObservationStatus.FINAL);
		input.getCode().addCoding().setSystem("http://loinc.org").setCode("12345");

		ValidationResult output = myVal.validateWithResult(input);
		List<SingleValidationMessage> errors = logResultsAndReturnNonInformationalOnes(output);
		assertThat(errors.size()).as(errors.toString()).isEqualTo(0);
	}

	@Test
	public void testValidateResourceWithExampleBindingCodeValidationPassingLoincWithExpansion() {
		Observation input = createObservationWithDefaultSubjectPerfomerEffective();

		ValueSetExpansionComponent expansionComponent = new ValueSetExpansionComponent();
		expansionComponent.addContains().setSystem("http://loinc.org").setCode("12345").setDisplay("Some display code");

//		mySupportedCodeSystemsForExpansion.put("http://loinc.org", expansionComponent);
		myInstanceVal.setValidationSupport(myValidationSupport);
		myMockSupport.addValidConcept("http://loinc.org", "12345");

		input.setStatus(ObservationStatus.FINAL);
		input.getCode().addCoding().setSystem("http://loinc.org").setCode("1234");

		ValidationResult output = myVal.validateWithResult(input);
		List<SingleValidationMessage> errors = logResultsAndReturnNonInformationalOnes(output);
		assertThat(errors).hasSize(1);
		assertEquals("Unknown code (for 'http://loinc.org#1234')", errors.get(0).getMessage());
	}

	@Test
	public void testValidateResourceWithExampleBindingCodeValidationPassingNonLoinc() {
		Observation input = createObservationWithDefaultSubjectPerfomerEffective();

		myInstanceVal.setValidationSupport(myValidationSupport);
		myMockSupport.addValidConcept("http://acme.org", "12345");

		input.setStatus(ObservationStatus.FINAL);
		input.getCode().addCoding().setSystem("http://acme.org").setCode("12345");

		ValidationResult output = myVal.validateWithResult(input);
		List<SingleValidationMessage> errors = logResultsAndReturnAll(output);
		assertThat(errors.size()).as(errors.toString()).isEqualTo(0);
	}

	@Test
	public void testValidateResourceWithValuesetExpansionBad() {

		Patient patient = new Patient();
		patient.addIdentifier().setSystem("http://example.com/").setValue("12345").getType().addCoding().setSystem("http://example.com/foo/bar").setCode("bar");

		ValidationResult output = myVal.validateWithResult(patient);
		List<SingleValidationMessage> all = logResultsAndReturnAll(output);
		assertThat(all).hasSize(1);
		assertEquals("Patient.identifier[0].type", all.get(0).getLocationString());
		assertThat(all.get(0).getMessage()).contains("None of the codings provided are in the value set 'Identifier Type Codes'");
		assertEquals(ResultSeverityEnum.WARNING, all.get(0).getSeverity());

	}

	@Test
	public void testValidateResourceWithValuesetExpansionGood() {
		Patient patient = new Patient();
		patient.addIdentifier().setSystem("http://system").setValue("12345").getType().addCoding().setSystem("http://hl7.org/fhir/v2/0203").setCode("MR");

		ValidationResult output = myVal.validateWithResult(patient);
		List<SingleValidationMessage> all = logResultsAndReturnAll(output);
		assertThat(all).isEmpty();
	}

	@Test
	@Disabled
	public void testValidateStructureDefinition() throws IOException {
		String input = loadResource("/sdc-questionnaire.profile.xml");

		ValidationResult output = myVal.validateWithResult(input);
		logResultsAndReturnAll(output);

		assertThat(output.getMessages().size()).as(output.toString()).isEqualTo(3);
		ourLog.info(output.getMessages().get(0).getLocationString());
		ourLog.info(output.getMessages().get(0).getMessage());
	}

	@Test
	public void testInvocationOfValidatorFetcher() throws IOException {
		String input = ClasspathUtil.loadResource("/dstu3-rick-test.json");

		when(policyAdvisor.policyForElement(any(), any(),any(),any(),any())).thenReturn(EnumSet.allOf(IValidationPolicyAdvisor.ElementValidationAction.class));
		when(policyAdvisor.policyForCodedContent(any(),any(),any(),any(),any(),any(),any(),any(),any())).thenReturn(EnumSet.allOf(IValidationPolicyAdvisor.CodedContentValidationAction.class));


		when(policyAdvisor.policyForReference(any(), any(), any(), any(), any())).thenReturn(ReferenceValidationPolicy.CHECK_TYPE_IF_EXISTS);
		when(policyAdvisor.policyForReference(any(), any(), any(), any(), any())).thenReturn(ReferenceValidationPolicy.CHECK_TYPE_IF_EXISTS);
		myInstanceVal.setValidatorResourceFetcher(fetcher);
		myInstanceVal.setValidatorPolicyAdvisor(policyAdvisor);
		myVal.validateWithResult(input);

		verify(fetcher, times(2)).resolveURL(any(), any(), anyString(), anyString(), anyString(), anyBoolean(), anyList());
		verify(policyAdvisor, times(4)).policyForReference(any(), any(), anyString(), anyString(), any());
		verify(fetcher, times(4)).fetch(any(), any(), anyString());
	}

	@Test
	public void testValueWithWhitespace() throws IOException {
		myMockSupport.addValidConcept("http://loinc.org", "34133-1");

		String input = ClasspathUtil.loadResource("/dstu3-rick-test.json");
		ValidationResult results = myVal.validateWithResult(input);
		List<SingleValidationMessage> outcome = logResultsAndReturnNonInformationalOnes(results);
		assertThat(outcome).hasSize(2);
		assertThat(outcome.toString()).contains("value should not start or finish with whitespace");

	}

	@AfterAll
	public static void afterClassClearContext() {
		ourCtx = null;
		TestUtil.randomizeLocaleAndTimezone();
	}


}
