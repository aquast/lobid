/* Copyright 2013-2015 Fabian Steeg, hbz. Licensed under the Eclipse Public License 1.0 */

package models.queries;

import static org.elasticsearch.index.query.QueryBuilders.matchQuery;
import static org.elasticsearch.index.query.QueryBuilders.multiMatchQuery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.elasticsearch.common.geo.GeoHashUtils;
import org.elasticsearch.common.geo.GeoPoint;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.FilterBuilder;
import org.elasticsearch.index.query.FilterBuilders;
import org.elasticsearch.index.query.GeoDistanceFilterBuilder;
import org.elasticsearch.index.query.GeoPolygonFilterBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder.Operator;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;

/**
 * Queries on the lobid-resources index.
 * 
 * @author Fabian Steeg (fsteeg)
 */
public class LobidResources {

	/**
	 * Query against all fields.
	 */
	public static class AllFieldsQuery extends AbstractIndexQuery {
		@Override
		public List<String> fields() {
			final List<String> searchFields = new ArrayList<>(Arrays.asList("_all"));
			final List<String> suggestFields = new NameQuery().fields();
			searchFields.addAll(suggestFields);
			return searchFields;
		}

		@Override
		public QueryBuilder build(final String queryString) {
			return QueryBuilders.queryString(queryString).field(fields().get(0));
		}
	}

	/**
	 * Query the lobid-resources index using a resource set.
	 */
	public static class SetQuery extends AbstractIndexQuery {

		@Override
		public List<String> fields() {
			return Arrays.asList("@graph.http://purl.org/dc/terms/isPartOf.@id",
					"@graph.http://purl.org/ontology/holding#collectedBy.@id");
		}

		@Override
		public QueryBuilder build(final String queryString) {
			final String prefix = "http://lobid.org/resource/";
			return multiMatchQuery(prefix + queryString.replace(prefix, ""),
					fields().toArray(new String[] {})).operator(Operator.AND);
		}

	}

	/**
	 * Query the lobid-resources index using a resource name.
	 */
	public static class NameQuery extends AbstractIndexQuery {

		@Override
		public List<String> fields() {
			List<String> fields = new ArrayList<>();
			fields.addAll(Arrays.asList(
					"@graph.http://purl.org/dc/terms/title.@value",
					"@graph.http://purl.org/dc/terms/alternative.@value",
					"@graph.http://rdvocab.info/Elements/otherTitleInformation.@value"));
			fields.addAll(new IdQuery().fields());
			return fields;
		}

		@Override
		public QueryBuilder build(final String queryString) {
			String[] fields = fields().toArray(new String[] {});
			fields[0] = fields[0] + "^3"; // boost the first field
			return multiMatchQuery(queryString, fields)
					.type(MultiMatchQueryBuilder.Type.CROSS_FIELDS)
					.operator(Operator.AND);
		}

	}

	/**
	 * Query the lobid-resources index using a resource author.
	 */
	public static class AuthorQuery extends AbstractIndexQuery {
		@Override
		public List<String> fields() {
			return Arrays.asList(
					"@graph.http://purl.org/lobid/lv#contributorLabel.@value",
					"@graph.http://d-nb.info/standards/elementset/gnd#dateOfBirth.@value",
					"@graph.http://d-nb.info/standards/elementset/gnd#dateOfDeath.@value",
					"@graph.http://purl.org/lobid/lv#contributorLabel.@value",
					"@graph.http://purl.org/dc/terms/creator.@id",
					"@graph.http://purl.org/dc/terms/contributor.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/act.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/aft.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/aui.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/aus.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/clb.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/cmp.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/cnd.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/cng.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/col.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/ctg.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/drt.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/dte.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/egr.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/ill.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/ive.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/ivr.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/mus.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/pht.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/prf.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/pro.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/sng.@id",
					"@graph.http://id.loc.gov/vocabulary/relators/hnr.@id",
					"@graph.http://purl.org/ontology/bibo/translator.@id",
					"@graph.http://purl.org/ontology/bibo/editor.@id");
		}

		@Override
		public QueryBuilder build(final String queryString) {
			return searchAuthor(queryString, LobidResources.class);
		}
	}

	/**
	 * Query the lobid-resources index using a resource subject.
	 */
	public static class SubjectQuery extends AbstractIndexQuery {
		@Override
		public List<String> fields() {
			return Arrays.asList(/* @formatter:off*/
					"@graph.http://purl.org/lobid/lv#subjectChain.@value",
					"@graph.http://purl.org/lobid/lv#subjectLabel.@value",
					"@graph.http://purl.org/dc/terms/subject");/* @formatter:on */
		}

		@Override
		public QueryBuilder build(final String queryString) {
			BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
			String queryValues = withoutBooleanOperator(queryString);
			boolean isAndQuery = isAndQuery(queryString);
			boolean hasLabel = hasLabel(queryValues);
			if (!queryString.contains("http") && Arrays.asList(queryString.split(","))
					.stream().filter(x -> x.trim().matches("[\\d\\-X]+")).count() == 0) {
				// no URI or GND-ID in queryString, ignore commas, e.g. "Ney, Elisabet":
				queryValues = queryString.replace(',', ' ');
			}
			for (String q : queryValues.split(",")) {
				String qTrimmed = q.trim();
				if (qTrimmed.startsWith("http") || qTrimmed.matches("[\\d\\-X]+")) {
					final String query = qTrimmed.startsWith("http://") ? qTrimmed
							: "http://d-nb.info/gnd/" + qTrimmed;
					final MatchQueryBuilder subjectIdQuery =
							matchQuery(fields().get(2) + ".@id", query.trim())
									.operator(Operator.AND);
					boolQuery = hasLabel || isAndQuery ? boolQuery.must(subjectIdQuery)
							: boolQuery.should(subjectIdQuery);
				} else {
					final MultiMatchQueryBuilder subjectLabelQuery =
							multiMatchQuery(qTrimmed, fields().get(0), fields().get(1))
									.operator(Operator.AND);
					boolQuery = hasLabel || isAndQuery ? boolQuery.must(subjectLabelQuery)
							: boolQuery.should(subjectLabelQuery);
				}
			}
			return boolQuery;
		}

		private static boolean hasLabel(String queryString) {
			return Arrays.asList(queryString.split(",")).stream().filter(
					x -> !x.trim().startsWith("http") && !x.trim().matches("[\\d\\-X]+"))
					.count() > 0;
		}
	}

	/**
	 * Query the lobid-resources index using a resource ID.
	 */
	public static class IdQuery extends AbstractIndexQuery {
		@Override
		public List<String> fields() {
			return Arrays.asList(/* @formatter:off*/
					"@graph.@id",
					"@graph.http://purl.org/lobid/lv#hbzID.@value",
					"@graph.http://purl.org/lobid/lv#zdbID.@value",
					"@graph.http://purl.org/ontology/bibo/isbn13.@value",
					"@graph.http://purl.org/ontology/bibo/isbn.@value",
					"@graph.http://purl.org/ontology/bibo/issn.@value",
					"@graph.http://purl.org/ontology/bibo/doi.@value",
					"@graph.http://purl.org/lobid/lv#urn.@value"); /* @formatter:on */
		}

		@Override
		public QueryBuilder build(final String queryString) {
			return multiMatchQuery(normalizedLobidResourceIdQueryString(queryString),
					fields().toArray(new String[] {})).operator(Operator.AND);
		}
	}

	private static String normalizedLobidResourceIdQueryString(
			final String queryString) {
		String normalizedQueryString = queryString.replaceAll(" ", "");
		if (!normalizedQueryString.contains("/")) // thus: doi or isbn
			if (normalizedQueryString.matches("\"?\\d.*\"?")) { // thus: isbn
				normalizedQueryString = normalizedQueryString.replaceAll("-", "");
			}
		final String hbzId = "\\p{L}+\\d+(-.+)?";
		return normalizedQueryString.matches(hbzId)
				? "http://lobid.org/resource/" + normalizedQueryString
				: normalizedQueryString;
	}

	/**
	 * Query the lobid-resources index for a given publisher.
	 */
	public static class PublisherQuery extends AbstractIndexQuery {

		@Override
		public List<String> fields() {
			return Arrays
					.asList("@graph.http://purl.org/dc/elements/1.1/publisher.@value");
		}

		@Override
		public QueryBuilder build(String queryString) {
			return multiMatchQuery(queryString, fields().toArray(new String[] {}))
					.operator(Operator.AND);
		}
	}

	/**
	 * Query the lobid-resources index for a given issued date.
	 */
	public static class IssuedQuery extends AbstractIndexQuery {

		@Override
		public List<String> fields() {
			return Arrays.asList("@graph.http://purl.org/dc/terms/issued.@value");
		}

		@Override
		public QueryBuilder build(String queryString) {
			final String[] elems = queryString.split("-");
			return elems.length == 2 ? //
					QueryBuilders.rangeQuery(fields().get(0)).gte(elems[0]).lte(elems[1])
					: multiMatchQuery(queryString, fields().toArray(new String[] {}));
		}
	}

	/**
	 * Query the lobid-resources index for a given medium.
	 */
	public static class MediumQuery extends AbstractIndexQuery {

		@Override
		public List<String> fields() {
			return Arrays.asList("@graph.http://purl.org/dc/terms/medium.@id");
		}

		@Override
		public QueryBuilder build(String queryString) {
			return multiValueMatchQuery(queryString);
		}

	}

	/**
	 * Query the lobid-resources index for a given NWBib subject classification.
	 */
	public static class NwBibSubjectQuery extends AbstractIndexQuery {

		@Override
		public List<String> fields() {
			return Arrays.asList("@graph.http://purl.org/lobid/lv#nwbibsubject.@id");
		}

		@Override
		public QueryBuilder build(String queryString) {
			return multiValueMatchQuery(queryString);
		}

	}

	/**
	 * Query the lobid-resources index for a given NWBib spatial classification.
	 */
	public static class NwBibSpatialQuery extends AbstractIndexQuery {

		@Override
		public List<String> fields() {
			return Arrays.asList("@graph.http://purl.org/lobid/lv#nwbibspatial.@id",
					"@graph.http://purl.org/dc/elements/1.1/coverage.@value");
		}

		@Override
		public QueryBuilder build(String queryString) {
			String queryValues = withoutBooleanOperator(queryString);
			BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
			for (String q : queryValues.split(",")) {
				MultiMatchQueryBuilder query =
						multiMatchQuery(q, fields().toArray(new String[] {}))
								.operator(Operator.AND);
				boolQuery = isAndQuery(queryString) ? boolQuery.must(query)
						: boolQuery.should(query);
			}
			return boolQuery;
		}

	}

	/**
	 * Query the lobid-resources set for results in a given point or polygon.
	 */
	public static class LocationQuery extends AbstractIndexQuery {

		@Override
		public List<String> fields() {
			return Arrays
					.asList("@graph.http://purl.org/lobid/lv#subjectLocation.@value");
		}

		@Override
		public QueryBuilder build(String queryString) {
			return QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(),
					polygonFilter(queryString));
		}

		private FilterBuilder polygonFilter(String location) {
			String[] points = location.split(" ");
			String field = "json-ld-lobid." + fields().get(0);
			FilterBuilder result = null;
			if (points.length == 1) {
				result = geoDistanceFilter(field, locationArray(points[0]));
			} else if (points.length == 2) {
				result = FilterBuilders.boolFilter()
						.should(geoDistanceFilter(field, locationArray(points[0])))
						.should(geoDistanceFilter(field, locationArray(points[1])));
			} else {
				GeoPolygonFilterBuilder filter = FilterBuilders.geoPolygonFilter(field);
				for (String point : points) {
					String[] latLon = locationArray(point);
					filter = filter.addPoint(Double.parseDouble(latLon[0].trim()),
							Double.parseDouble(latLon[1].trim()));
				}
				result = filter;
			}
			return result;
		}

		private static String[] locationArray(String loc) {
			String[] pointLocation = null;
			if (loc.contains(",")) {
				pointLocation = loc.split(",");
			} else {
				GeoPoint point = GeoHashUtils.decode(loc);
				pointLocation = new String[] { //
						String.valueOf(point.getLat()), String.valueOf(point.getLon()) };
			}
			return pointLocation;
		}

		private static GeoDistanceFilterBuilder geoDistanceFilter(String field,
				String[] latLon) {
			return FilterBuilders.geoDistanceFilter(field)
					.point(Double.parseDouble(latLon[0].trim()),
							Double.parseDouble(latLon[1].trim()))
					.distance("100m");
		}

	}

	/**
	 * Query the lobid-resources index using resource 'words'. This models a
	 * concept from the hbz union catalog, see
	 * https://github.com/hbz/nwbib/issues/110 for details.
	 */
	public static class WordQuery extends AbstractIndexQuery {
		@Override
		public List<String> fields() {
			List<String> fields = new ArrayList<>();
			fields.addAll(new SubjectQuery().fields());
			fields.addAll(new NwBibSubjectQuery().fields());
			fields.addAll(new NwBibSpatialQuery().fields());
			fields.addAll(new AuthorQuery().fields());
			fields.addAll(new NameQuery().fields());
			fields.addAll(new PublisherQuery().fields());
			return fields;
		}

		@Override
		public QueryBuilder build(String queryString) {
			QueryStringQueryBuilder builder = QueryBuilders.queryString(queryString);
			fields().stream().forEach(f -> builder.field(f));
			return builder;
		}
	}

	/**
	 * Query the lobid-resources index for corporations.
	 */
	public static class CorporationQuery extends AbstractIndexQuery {
		@Override
		public List<String> fields() {
			return Arrays.asList(
					"@graph.http://purl.org/lobid/lv#nameOfContributingCorporateBody.@value",
					"@graph.http://purl.org/dc/terms/creator.@id",
					"@graph.http://purl.org/dc/terms/contributor.@id");
		}

		@Override
		public QueryBuilder build(String queryString) {
			return multiMatchQuery(
					queryString.matches("\\d+(.+)")
							? "http://d-nb.info/gnd/" + queryString : queryString,
					fields().toArray(new String[] {})).operator(Operator.AND);
		}
	}

	/**
	 * Query the lobid-resources index for corporations.
	 */
	public static class ChangedSinceQuery extends AbstractIndexQuery {
		@Override
		public List<String> fields() {
			return Arrays.asList("http://purl.org/dc/terms/created.@value",
					"http://purl.org/dc/terms/modified.@value");
		}

		@Override
		public QueryBuilder build(String queryString) {
			return QueryBuilders.filteredQuery(QueryBuilders.matchAllQuery(),
					FilterBuilders.boolFilter()
							.should(FilterBuilders.queryFilter(
									QueryBuilders.rangeQuery(fields().get(0)).gte(queryString)))
							.should(FilterBuilders.queryFilter(
									QueryBuilders.rangeQuery(fields().get(1)).gte(queryString))));
		}
	}

}
