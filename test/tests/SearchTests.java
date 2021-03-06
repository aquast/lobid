/* Copyright 2012-2014 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package tests;

import static org.fest.assertions.Assertions.assertThat;
import static play.mvc.Http.Status.BAD_REQUEST;
import static play.mvc.Http.Status.OK;
import static play.test.Helpers.GET;
import static play.test.Helpers.fakeApplication;
import static play.test.Helpers.fakeRequest;
import static play.test.Helpers.route;
import static play.test.Helpers.running;
import static play.test.Helpers.status;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.junit.Assert;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;

import models.Document;
import models.Index;
import models.Parameter;
import models.Search;
import play.libs.Json;
import play.mvc.Result;

/**
 * Tests for the search functionality.
 * 
 * @author Fabian Steeg (fsteeg)
 */
@SuppressWarnings("javadoc")
public class SearchTests extends SearchTestsHarness {

	@Test
	public void accessIndex() {
		assertThat(
				client.prepareSearch().execute().actionGet().getHits().totalHits())
						.isEqualTo(55);
		JsonNode json = Json.parse(client
				.prepareGet(Index.LOBID_RESOURCES.id(), "json-ld-lobid",
						"http://lobid.org/resource/BT000001260")
				.execute().actionGet().getSourceAsString());
		assertThat(json.isObject()).isTrue();
	}

	@Test
	public void accessIndexUsingCollectionRoute() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final String response = call("resource/NWBib");
				assertThat(response).isNotNull();
				final JsonNode jsonObject = Json.parse(response);
				assertThat(jsonObject.asText().contains("Regionalbibliographien"));
			}
		});
	}

	@Test
	public void searchViaModel() {
		final List<Document> docs =
				new Search(ImmutableMap.of(Parameter.AUTHOR, "theo"),
						Index.LOBID_RESOURCES).documents();
		assertThat(docs.size()).isPositive();
		for (Document document : docs) {
			assertThat(document.getMatchedField()).contains("Hundt, Theo");
		}
	}

	@Test
	public void searchViaModelOrgName() {
		assertThat(searchOrgByName("Konstanz Universität")).isEqualTo(1);
		assertThat(searchOrgByName("Konstanz Schmeckermeck")).isEqualTo(0);
	}

	@Test
	public void searchViaModelOrgNameAltLabel() {
		assertThat(searchOrgByName("Universität Konstanz KIM")).isEqualTo(1);
		assertThat(searchOrgByName("Universitaet Konstanz KIM")).isEqualTo(1);
		assertThat(searchOrgByName("Universitat Konstanz KIM")).isEqualTo(1);
	}

	private static int searchOrgByName(final String term) {
		return new Search(ImmutableMap.of(Parameter.NAME, term),
				Index.LOBID_ORGANISATIONS).documents().size();
	}

	@Test
	public void searchViaModelOrgQuery() {
		assertThat(searchOrgQuery("1,000,001 and more")).isEqualTo(2);
		assertThat(searchOrgQuery("Konstanz Schmeckermeck")).isEqualTo(1);
	}

	private static int searchOrgQuery(final String term) {
		return new Search(ImmutableMap.of(Parameter.Q, term),
				Index.LOBID_ORGANISATIONS).documents().size();
	}

	/*@formatter:off*/
	@Test public void searchViaModelOrgIdShort() { searchOrgById("DE-605"); }
	@Test public void searchViaModelOrgIdLong() { searchOrgById("http://lobid.org/organisation/DE-605"); }
	/*@formatter:on*/

	private static void searchOrgById(final String term) {
		final List<Document> docs = new Search(ImmutableMap.of(Parameter.ID, term),
				Index.LOBID_ORGANISATIONS).documents();
		assertThat(docs.size()).isEqualTo(1);
	}

	/*@formatter:off*/
	@Test public void searchResByIdTT() { searchResById("TT002234003"); }
	@Test public void searchResByIdHT() { searchResById("HT002189125"); }
	@Test public void searchResByIdZdb1() { searchResById("ZDB2615620-9"); }
	@Test public void searchResByIdZdb2() { searchResById("ZDB2530091-X"); }
	@Test public void searchResByIdTTUrl() { searchResById("http://lobid.org/resource/TT002234003"); }
	@Test public void searchResByIdHTUrl() { searchResById("http://lobid.org/resource/HT002189125"); }
	@Test public void searchResByIdZdbUrl1() { searchResById("http://lobid.org/resource/ZDB2615620-9"); }
	@Test public void searchResByIdZdbUrl2() { searchResById("http://lobid.org/resource/ZDB2530091-X"); }
	@Test public void searchResByIdISBN() { searchResById("0940450003"); }
	@Test public void searchResByIdIsbnConsistingOfHyphenAndSpaceISBN() { searchResById("0 080 238-70x"); }
	@Test public void searchResByIdUrn() { searchResById("urn:nbn:de:hbz:929:02-1035"); }
	@Test public void searchResByDoi() { searchResById("10.1007/978-1-4020-8389-1"); }

	/*@formatter:on*/

	private static void searchResById(final String term) {
		final List<Document> docs =
				new Search(ImmutableMap.of(Parameter.ID, term), Index.LOBID_RESOURCES)
						.documents();
		assertThat(docs.size()).isEqualTo(1);
	}

	@Test
	public void searchResByIdWithReturnFieldViaModel() {
		running(fakeApplication(), new Runnable() {
			@Override
			public void run() {
				final List<Document> docs =
						new Search(ImmutableMap.of(Parameter.ID, "TT050326640"),
								Index.LOBID_RESOURCES).field("fulltextOnline").documents();
				assertThat(docs.size()).isEqualTo(1);
				assertThat(docs.get(0).getSource())
						.isEqualTo("[\"http://dx.doi.org/10.1007/978-1-4020-8389-1\"]");
			}
		});
	}

	/*@formatter:off*/
	@Test public void returnFieldPathOneHit() { returnFieldHitPath("resource/TT050326640?", 1); }
	@Test public void returnFieldPathNoHit() { returnFieldHitPath("resource/HT000000716?", 0); }
	/*@formatter:on*/

	public void returnFieldHitPath(final String query, final int hits) {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final String response = call(query + "format=short.fulltextOnline");
				assertThat(response).isNotNull();
				final JsonNode jsonObject = Json.parse(response);
				assertThat(jsonObject.isArray()).isTrue();
				assertThat(jsonObject.size()).isEqualTo(hits);
				if (hits > 0)
					assertThat(jsonObject.get(0).asText())
							.isEqualTo("http://dx.doi.org/10.1007/978-1-4020-8389-1");
			}
		});
	}

	/*@formatter:off*/
	@Test public void returnFieldParamOneHit() { returnFieldHitParam("resource?id=TT050326640&", 1); }
	@Test public void returnFieldParamNoHit() { returnFieldHitParam("resource?id=HT000000716&", 0); }
	/*@formatter:on*/

	public void returnFieldHitParam(final String query, final int hits) {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final String response = call(query + "format=short.fulltextOnline");
				assertThat(response).isNotNull();
				final JsonNode jsonObject = Json.parse(response);
				assertThat(jsonObject.isArray()).isTrue();
				assertThat(jsonObject.size()).isEqualTo(hits + META);
			}
		});
	}

	@Test
	public void returnFieldSorting() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final String response =
						call("resource?author=abraham&format=short.issued");
				assertThat(response).isNotNull();
				final JsonNode jsonObject = Json.parse(response);
				assertThat(jsonObject.isArray()).isTrue();
				assertThat(jsonObject.get(0 + META).asText()).isEqualTo("1719");
				assertThat(jsonObject.get(1 + META).asText()).isEqualTo("1906");
				assertThat(jsonObject.get(2 + META).asText()).isEqualTo("1973");
				assertThat(jsonObject.get(3 + META).asText()).isEqualTo("1976");
				assertThat(jsonObject.get(4 + META).asText()).isEqualTo("1977");
				assertThat(jsonObject.get(5 + META).asText()).isEqualTo("1979");
				assertThat(jsonObject.get(6 + META).asText()).isEqualTo("1981");
			}
		});
	}

	@Test
	public void returnFieldBadRequest() {
		running(fakeApplication(), new Runnable() {
			@Override
			public void run() {
				assertThat(status(
						route(fakeRequest(GET, "/resource?author=Böll&format=ids.issued"))))
								.isEqualTo(BAD_REQUEST);
			}
		});
	}

	@Test
	public void returnScrollSizeBadRequest() {
		running(fakeApplication(), new Runnable() {
			@Override
			public void run() {
				assertThat(status(
						route(fakeRequest(GET, "/resource?q=*&size=10000&scroll=true")
								.withHeader("Accept", "application/rdf+xml"))))
										.isEqualTo(BAD_REQUEST);
			}
		});
	}

	/*@formatter:off*/
	@Test public void searchViaModelBirth0() { findOneBy("Theo Hundt"); }
	@Test public void searchViaModelBirth1() { findOneBy("Hundt, Theo"); }
	@Test public void searchViaModelBirth2() { findOneBy("Theo Hundt"); }
	@Test public void searchViaModelBirth3() { findOneBy("Goeters, J. F. Gerhard"); }
	@Test public void searchViaModelBirth4() { findOneBy("Göters, J. F. Gerhard"); }
	@Test public void searchViaModelMulti1() { findOneBy("Vollhardt, Kurt Peter C."); }
	@Test public void searchViaModelMulti2() { findOneBy("Kurt Peter C. Vollhardt"); }
	@Test public void searchViaModelMulti3() { findOneBy("Vollhardt, Kurt Peter C."); }
	@Test public void searchViaModelMulti4() { findOneBy("Neil Eric Schore"); }
	@Test public void searchViaModelMulti5() { findOneBy("131392786"); }
	@Test public void searchViaModelMulti6() { findOneBy("http://d-nb.info/gnd/131392786"); }
	/*@formatter:on*/

	private static void findOneBy(String name) {
		assertThat(new Search(ImmutableMap.of(Parameter.AUTHOR, name),
				Index.LOBID_RESOURCES).documents().size()).isEqualTo(1);
	}

	@Test
	public void searchViaModelMultiResult() {
		List<Document> documents =
				new Search(ImmutableMap.of(Parameter.AUTHOR, "Neil Eric Schore"),
						Index.LOBID_RESOURCES).documents();
		assertThat(documents.size()).isEqualTo(1);
		assertThat(documents.get(0).getMatchedField()).isEqualTo("Schore, Neil");
	}

	@Test
	public void searchViaModelSetNwBib() {
		List<Document> documents =
				new Search(ImmutableMap.of(Parameter.SET, "NwBib"),
						Index.LOBID_RESOURCES).documents();
		assertThat(documents.size()).isEqualTo(4);
		assertThat(documents.get(2).getMatchedField())
				.isEqualTo("http://lobid.org/resource/NWBib");
	}

	@Test
	public void indexRoute() {
		running(fakeApplication(), new Runnable() {
			@Override
			public void run() {
				Result result = route(fakeRequest(GET, "/"));
				assertThat(status(result)).isEqualTo(OK);
			}
		});
	}

	@Test
	public void searchViaApiPageEmpty() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				assertThat(call("")).contains("<html");
			}
		});
	}

	@Test
	public void searchViaApiHtml() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				assertThat(call("resource?author=abraham", "text/html"))
						.contains("<html");
			}
		});
	}

	@Test
	public void searchViaApiFull() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final JsonNode jsonObject =
						Json.parse(call("resource?author=abraham&format=full"));
				assertThat(jsonObject.isArray()).isTrue();
				assertThat(jsonObject.size())//
						.isGreaterThan(5 + META).isLessThan(10 + META);
				assertThat(jsonObject.get(0 + META).isContainerNode()).isTrue();
			}
		});
	}

	@Test
	public void searchViaApiShort() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final JsonNode jsonObject =
						Json.parse(call("resource?author=abraham&format=short"));
				assertThat(jsonObject.isArray()).isTrue();
				assertThat(jsonObject.size()).isGreaterThan(5).isLessThan(10);
				assertThat(jsonObject.elements().next().isContainerNode()).isFalse();
			}
		});
	}

	private static List<String> list(JsonNode jsonObject) {
		List<String> list = new ArrayList<>();
		Iterator<JsonNode> elements = jsonObject.elements();
		while (elements.hasNext()) {
			list.add(elements.next().asText());
		}
		return list;
	}

	@Test
	public void searchViaApiGnd() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final JsonNode jsonObject =
						Json.parse(call("person?name=bach&format=short&t="
								+ "http://d-nb.info/standards/elementset/gnd%23DifferentiatedPerson"));
				assertThat(jsonObject.isArray()).isTrue();
				/* differentiated & *starting* with 'bach' only & no dupes */
				assertThat(jsonObject.size()).isEqualTo(4);
			}
		});
	}

	/* @formatter:off */
	@Test public void searchAltNamePlain() { searchName("Schmidt, Loki", 1); }
	@Test public void searchAltNameSwap()  { searchName("Loki Schmidt", 1); }
	@Test public void searchAltNameSecond(){ searchName("Hannelore Glaser", 1); }
	@Test public void searchAltNameShort() { searchName("Loki", 1); }
	@Test public void searchAltNameNgram() { searchName("Lok", 1); }
	@Test public void searchPrefNameNgram(){ searchName("Hanne", 1); }
	@Test public void searchAltNameDates() { searchName("Loki Schmidt (1919-2010)", 1); }
	@Test public void searchAltNameBirth() { searchName("Loki Schmidt (1919-)", 1); }
	@Test public void searchAltNameNone()  { searchName("Loki Müller", 0); }
	/* @formatter:on */

	private static void searchName(final String name, final int results) {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final JsonNode jsonObject = Json.parse(
						call("person?name=" + name.replace(" ", "%20") + "&format=short"));
				assertThat(jsonObject.isArray()).isTrue();
				assertThat(jsonObject.size()).isEqualTo(results);
				if (results > 0) {
					assertThat(Iterables.any(list(jsonObject), new Predicate<String>() {
						@Override
						public boolean apply(String s) {
							return s.equals("Schmidt, Loki (1919-03-03-2010-10-21)");
						}
					})).isTrue();
				}
			}
		});
	}

	@Test
	public void searchViaApiResourcesAuthorId() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				String gndId = "118554808";
				final JsonNode jsonObject =
						Json.parse(call("resource?author=" + gndId));
				assertThat(jsonObject.isArray()).isTrue();
				assertThat(jsonObject.size()).isEqualTo(1 + META);
				assertThat(jsonObject.get(0 + META).toString())
						.contains("http://d-nb.info/gnd/" + gndId);
			}
		});
	}

	/* @formatter:off */
	@Test public void resourceByGndSubjectMulti1(){resByGndSubject("4062901-6", 1);}
	@Test public void resourceByGndSubjectMulti2(){resByGndSubject("4066438-7", 1);}
	@Test public void resourceByGndSubjectMulti3Default(){resByGndSubject("4062901-6,4077548-3", 2);}
	@Test public void resourceByGndSubjectMulti3Or(){resByGndSubject("4062901-6,4077548-3,OR", 2);}
	@Test public void resourceByGndSubjectMulti3And(){resByGndSubject("4062901-6,4077548-3,AND", 0);}
	@Test public void resourceByGndSubjectMulti4(){resByGndSubject("4066438-7,4077548-3", 1);}
	@Test public void resourceByGndSubjectDashed(){resByGndSubject("4414195-6", 1);}
	@Test public void resourceByGndSubjectSingle(){resByGndSubject("189452846", 1);}
	@Test public void resourceByGndSubjectMultiUri(){resByGndSubject("http://d-nb.info/gnd/4062901-6", 1);}
	@Test public void resourceByGndSubjectDashedUri(){resByGndSubject("http://d-nb.info/gnd/4414195-6", 1);}
	@Test public void resourceByGndSubjectSingleUri(){resByGndSubject("http://d-nb.info/gnd/189452846", 1);}
	/* @formatter:on */

	public void resByGndSubject(final String gndId, final int results) {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final JsonNode jsonObject =
						Json.parse(call("resource?subject=" + gndId));
				assertThat(jsonObject.isArray()).isTrue();
				assertThat(jsonObject.size()).isEqualTo(results + META);
				if (results > 0) {
					String prefix = "http://d-nb.info/gnd/";
					for (String id : gndId.replaceAll(",AND|,OR", "").split(",")) {
						assertThat(jsonObject.toString())
								.contains(prefix + id.replace(prefix, ""));
					}
				}
			}
		});
	}

	@Test
	public void resByGndSubjectLabel() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final JsonNode jsonObject = Json.parse(call("resource?subject=UdSSR"));
				assertThat(jsonObject.isArray()).isTrue();
				assertThat(jsonObject.size()).isEqualTo(1 + META);
				assertThat(jsonObject.get(0 + META).toString())
						.contains("PERGAMON POLICY STUDIES");
			}
		});
	}

	@Test
	public void resByNwBibSubjectUri() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final JsonNode jsonObject = Json.parse(
						call("resource?nwbibsubject=http://purl.org/lobid/nwbib#s552000"));
				assertThat(jsonObject.isArray()).isTrue();
				assertThat(jsonObject.size()).isEqualTo(1 + META);
			}
		});
	}

	/* @formatter:off */
	@Test public void personByGndIdNumeric(){gndPerson("1019737174", 1);}
	@Test public void personByGndIdAlphaNumeric(){gndPerson("11850553X", 1);}
	@Test public void personByGndIdNumericFull(){gndPerson("http://d-nb.info/gnd/1019737174", 1);}
	@Test public void personByGndIdAlphaNumericFull(){gndPerson("http://d-nb.info/gnd/11850553X", 1);}
	/* @formatter:on */

	public void gndPerson(final String gndId, final int results) {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final JsonNode jsonObject = Json.parse(call("person?id=" + gndId));
				assertThat(jsonObject.isArray()).isTrue();
				assertThat(jsonObject.size()).isEqualTo(results + META);
				final String gndPrefix = "http://d-nb.info/gnd/";
				assertThat(jsonObject.get(0 + META).toString())
						.contains(gndPrefix + gndId.replace(gndPrefix, ""));
			}
		});
	}

	/* @formatter:off */
	@Test public void subjectByGndId1Preferred(){gndSubject("Herbstadt-Ottelmannshausen", 1);}
	@Test public void subjectByGndId1PreferredNGram(){gndSubject("Ottel", 1);}
	@Test public void subjectByGndId1Variant(){gndSubject("Ottelmannshausen <Herbstadt>", 1);}
	@Test public void subjectByGndId1VariantNGram(){gndSubject("Herb", 1);}
	@Test public void subjectByGndId2Preferred(){gndSubject("Kirchhundem-Heinsberg", 1);}
	@Test public void subjectByGndId2Variant(){gndSubject("Heinsberg <Kirchhundem>", 1);}
	/* @formatter:on */

	public void gndSubject(final String subjectName, final int results) {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final JsonNode jsonObject =
						Json.parse(call("subject?name=" + subjectName));
				assertThat(jsonObject.isArray()).isTrue();
				assertThat(jsonObject.size()).isEqualTo(results + META);
				assertThat(jsonObject.get(0 + META).toString()).contains(subjectName);
			}
		});
	}

	@Test
	public void subjectByGndIdAlphaNumericPlusDashFull() {
		gndSubjectId("http://d-nb.info/gnd/10115480-X", 1);
	}

	public void gndSubjectId(final String gndId, final int results) {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final JsonNode jsonObject = Json.parse(call("subject?id=" + gndId));
				assertThat(jsonObject.isArray()).isTrue();
				assertThat(jsonObject.size()).isEqualTo(results + META);
				final String gndPrefix = "http://d-nb.info/gnd/";
				assertThat(jsonObject.get(0 + META).toString())
						.contains(gndPrefix + gndId.replace(gndPrefix, ""));
			}
		});
	}

	/* @formatter:off */
	@Test public void itemByIdParam1(){findItem("item?id=BT000000079:DE-Sol1:GA%20644");}
	@Test public void itemByIdParam2(){findItem("item?id=BT000001260:DE-Sol1:MA%20742");}
	@Test public void itemByIdUri1(){findItem("item?id=http://lobid.org/item/BT000000079:DE-Sol1:GA%20644");}
	@Test public void itemByIdUri2(){findItem("item?id=http://lobid.org/item/BT000001260:DE-Sol1:MA%20742");}
	@Test public void itemByName(){findItem("item?name=GA+644");}
	/* @formatter:on */

	public void findItem(final String call) {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final JsonNode jsonObject = Json.parse(call(call));
				assertThat(jsonObject.isArray()).isTrue();
				assertThat(jsonObject.size()).isEqualTo(1 + META);
			}
		});
	}

	@Test
	public void itemByIdRoute() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final JsonNode jsonObject =
						Json.parse(call("item/BT000000079:DE-Sol1:GA%20644"));
				assertThat(jsonObject.isArray()).isTrue();
				assertThat(jsonObject.size()).isEqualTo(1);
			}
		});
	}

	private final static String ENDPOINT = "resource?author=abraham";

	@Test
	public void searchViaApiWithContentNegotiationNTriples() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final String response = call(ENDPOINT, "text/plain");
				assertThat(response).isNotEmpty().startsWith("<http");
				assertThat(response)
						.contains("<http://xmlns.com/foaf/0.1/primaryTopic>");
			}
		});
	}

	@Test
	public void searchViaApiWithContentNegotiationTurtle() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final String response = call(ENDPOINT, "text/turtle");
				assertThat(response).isNotEmpty().contains("      a       ");
				assertThat(response)
						.contains("<http://xmlns.com/foaf/0.1/primaryTopic>");
			}
		});
	}

	@Test
	public void searchViaApiWithContentNegotiationRdfa() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				assertThat(call(ENDPOINT, "text/html")).isNotEmpty()
						.contains("<!DOCTYPE html>");
			}
		});
	}

	@Test
	public void searchViaApiWithContentNegotiationRdfXml() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				String response = call(ENDPOINT, "application/rdf+xml");
				assertThat(response).isNotEmpty().contains("<rdf:RDF");
				try {
					assertThat(DocumentBuilderFactory.newInstance().newDocumentBuilder()
							.parse(new InputSource(new StringReader(response)))).isNotNull();
				} catch (SAXException | IOException | ParserConfigurationException e) {
					e.printStackTrace();
					Assert.fail(e.getMessage());
				}
			}
		});
	}

	@Test
	public void searchViaApiWithContentNegotiationN3() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final String n3 = call(ENDPOINT, "text/n3"); // NOPMD
				assertThat(n3).isNotEmpty();
			}
		});
	}

	@Test
	public void searchViaApiWithContentNegotiationJson() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				assertJsonResponse(call(ENDPOINT, "application/json"));
			}
		});
	}

	@Test
	public void searchViaApiWithContentNegotiationDefault() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				assertJsonResponse(call(ENDPOINT, "*/*"));
			}
		});
	}

	@Test
	public void searchViaApiWithContentNegotiationOverrideWithParam() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				assertJsonResponse(call(ENDPOINT + "&format=full", "text/html"));
			}
		});
	}

	private static void assertJsonResponse(final String response) {
		assertThat(response).isNotEmpty().startsWith("[{\"@").contains("@context")
				.contains("@graph").endsWith("}]");
	}

	@Test
	public void searchViaApiWithContentNegotiationBrowser() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				assertThat(call(ENDPOINT,
						"text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"))
								.isNotEmpty().contains("<html");
			}
		});
	}

	@Test
	public void searchWithLimit() {
		final Index index = Index.LOBID_RESOURCES;
		final Parameter parameter = Parameter.AUTHOR;
		assertThat(new Search(ImmutableMap.of(parameter, "Abraham"), index)
				.page(0, 3).documents().size()).isEqualTo(3);
		assertThat(new Search(ImmutableMap.of(parameter, "Abraham"), index)
				.page(3, 7).documents().size()).isEqualTo(6);
	}

	@Test(expected = IllegalArgumentException.class)
	public void searchWithLimitInvalidFrom() {
		new Search(ImmutableMap.of(Parameter.AUTHOR, "ha"), Index.LOBID_RESOURCES)
				.page(-1, 3).documents();
	}

	@Test(expected = IllegalArgumentException.class)
	public void searchWithLimitInvalidSize() {
		new Search(ImmutableMap.of(Parameter.AUTHOR, "ha"), Index.LOBID_RESOURCES)
				.page(0, 1001).documents();
	}

	@Test
	public void searchWithLimitApi() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				assertThat(call("resource?author=ha&from=0&size=3")).isNotEmpty()
						.isNotEqualTo(call("resource?author=ha&from=3&size=6"));
			}
		});
	}

	@Test
	public void searchWithLimitApiDefaults() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				String r1 = "resource?author=ha&from=0&size=3";
				String r2 = "resource?author=ha&size=3";
				assertThat(call(r1).replace(r1, ""))
						.isEqualTo(call(r2).replace(r2, "")); /* default 'from' is 0 */
				String r3 = "resource?author=ha&from=10&size=50";
				String r4 = "resource?author=ha&from=10";
				assertThat(call(r3).replace(r3, ""))
						.isEqualTo(call(r4).replace(r4, "")); /* default 'size' is 50 */
			}
		});
	}

	@Test
	public void testIdAndPrimaryTopicForResource() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final JsonNode jsonObject = Json.parse(call("resource?id=BT000001260"));
				assertThat(jsonObject.isArray()).isTrue();
				assertThat(jsonObject.get(0 + META).get("@id").asText())
						.isEqualTo("http://lobid.org/resource/BT000001260/about");
				assertThat(jsonObject.get(0 + META).get("primaryTopic").asText())
						.isEqualTo("http://lobid.org/resource/BT000001260");
			}
		});
	}

	@Test
	public void testIdAndPrimaryTopicForPerson() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				final JsonNode jsonObject = Json.parse(call("person?id=1019737174"));
				assertThat(jsonObject.isArray()).isTrue();
				assertThat(jsonObject.get(0 + META).get("@id").asText())
						.isEqualTo("http://d-nb.info/gnd/1019737174/about");
				assertThat(jsonObject.get(0 + META).get("primaryTopic").asText())
						.isEqualTo("http://d-nb.info/gnd/1019737174");
			}
		});
	}

	@Test
	public void testAllHitsInResultJson() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				String request = "resource?q=*&from=0&size=3";
				String response = call(request);
				assertThat(response).contains(request);
				assertThat(response)
						.contains("\"http://sindice.com/vocab/search#totalResults\":27}");
			}
		});
	}

	@Test
	public void testAllHitsInResultNTriples() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				String request = "resource?q=*&from=0&size=3";
				String response = call(request, "text/plain");
				assertThat(response).contains(request);
				assertThat(response)
						.contains("<http://sindice.com/vocab/search#totalResults> "
								+ "\"27\"^^<http://www.w3.org/2001/XMLSchema#integer>");
			}
		});
	}

	@Test
	public void testAllHitsNotInPathResultNTripelsJson() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				String request = "resource/BT000001260";
				String response = call(request);
				assertThat(response)
						.doesNotContain("http://sindice.com/vocab/search#totalResults");
			}
		});
	}

	@Test
	public void testAllHitsNotInPathResultNTripels() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				String request = "resource/BT000001260";
				String response = call(request, "text/plain");
				assertThat(response)
						.doesNotContain("<http://sindice.com/vocab/search#totalResults>");
			}
		});
	}

	@Test
	public void testScrollScanWithDate() {
		running(TEST_SERVER, new Runnable() {
			@Override
			public void run() {
				String request = "resource?q=*&scroll=20020201";
				String response = call(request, "text/plain");
				assertThat(response).hasSize(157970);
				request = "resource?q=*&scroll=20090201";
				response = call(request, "text/plain");
				assertThat(response).isNullOrEmpty();
			}
		});
	}

}
