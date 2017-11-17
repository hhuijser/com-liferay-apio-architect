/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.vulcan.writer;

import static com.liferay.vulcan.pagination.PageType.CURRENT;
import static com.liferay.vulcan.pagination.PageType.FIRST;
import static com.liferay.vulcan.pagination.PageType.LAST;
import static com.liferay.vulcan.pagination.PageType.NEXT;
import static com.liferay.vulcan.pagination.PageType.PREVIOUS;
import static com.liferay.vulcan.writer.url.URLCreator.createCollectionPageURL;
import static com.liferay.vulcan.writer.url.URLCreator.createCollectionURL;

import com.google.gson.JsonObject;

import com.liferay.vulcan.function.TriFunction;
import com.liferay.vulcan.list.FunctionalList;
import com.liferay.vulcan.message.json.JSONObjectBuilder;
import com.liferay.vulcan.message.json.PageMessageMapper;
import com.liferay.vulcan.pagination.Page;
import com.liferay.vulcan.pagination.SingleModel;
import com.liferay.vulcan.request.RequestInfo;
import com.liferay.vulcan.resource.Representor;
import com.liferay.vulcan.resource.identifier.Identifier;
import com.liferay.vulcan.uri.Path;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Function;

/**
 * An instance of this class can be used to write a page.
 *
 * @author Alejandro Hernández
 * @review
 */
public class PageWriter<T> {

	/**
	 * This method can be used to create a new {@code PageWriter} object,
	 * without creating the builder.
	 *
	 * @param  function the function that transforms a builder into a {@code
	 *         PageWriter}
	 * @return the {@code PageWriter} instance
	 */
	public static <T> PageWriter<T> create(
		Function<Builder<T>, PageWriter<T>> function) {

		return function.apply(new Builder<>());
	}

	public PageWriter(Builder<T> builder) {
		_pageMessageMapper = builder._pageMessageMapper;
		_page = builder._page;
		_pageJsonObjectBuilder = new JSONObjectBuilder();
		_requestInfo = builder._requestInfo;
		_representorFunction = builder._representorFunction;
		_pathFunction = builder._pathFunction;
		_resourceNameFunction = builder._resourceNameFunction;
	}

	/**
	 * Write the handled {@link Page} to a String. It uses a {@link
	 * FieldsWriter} in order to write the different fields of the {@link
	 * Representor} of its items. If no {@code Representor} or {@code Path} can
	 * be found for the model, {@code Optional#empty()} is returned.
	 *
	 * @return the representation of the {@code Page} if both its {@code
	 *         Representor} and {@code Path} are available; returns {@code
	 *         Optional#empty()} otherwise
	 * @review
	 */
	public String write() {
		_pageMessageMapper.onStart(
			_pageJsonObjectBuilder, _page, _requestInfo.getHttpHeaders());

		_pageMessageMapper.mapItemTotalCount(
			_pageJsonObjectBuilder, _page.getTotalCount());

		Collection<T> items = _page.getItems();

		_pageMessageMapper.mapPageCount(_pageJsonObjectBuilder, items.size());

		_writePageURLs();

		Optional<String> optional = _getCollectionURLOptional();

		optional.ifPresent(
			url -> _pageMessageMapper.mapCollectionURL(
				_pageJsonObjectBuilder, url));

		items.forEach(
			model -> _writeItem(
				new SingleModel<>(model, _page.getModelClass())));

		_pageMessageMapper.onFinish(
			_pageJsonObjectBuilder, _page, _requestInfo.getHttpHeaders());

		JsonObject jsonObject = _pageJsonObjectBuilder.build();

		return jsonObject.toString();
	}

	/**
	 * Use instances of this builder to create {@link PageWriter} instances.
	 *
	 * @review
	 */
	public static class Builder<T> {

		/**
		 * Add information about the page being written to the builder.
		 *
		 * @param  page the page being written
		 * @return the updated builder
		 */
		public PageMessageMapperStep page(Page<T> page) {
			_page = page;

			return new PageMessageMapperStep();
		}

		public class BuildStep {

			/**
			 * Constructs and returns a {@link PageWriter} instance with the
			 * information provided to the builder.
			 *
			 * @return the {@code SingleModelWriter} instance
			 * @review
			 */
			public PageWriter<T> build() {
				return new PageWriter<>(Builder.this);
			}

		}

		public class PageMessageMapperStep {

			/**
			 * Add information about the {@code PageMessageMapper} to the
			 * builder.
			 *
			 * @param  pageMessageMapper the {@code PageMessageMapper} headers.
			 * @return the updated builder.
			 * @review
			 */
			public PathFunctionStep pageMessageMapper(
				PageMessageMapper<T> pageMessageMapper) {

				_pageMessageMapper = pageMessageMapper;

				return new PathFunctionStep();
			}

		}

		public class PathFunctionStep {

			/**
			 * Add information to the builder about the function that can be
			 * used to convert an {@code Identifier} into a {@code Path}.
			 *
			 * @param  pathFunction the function to map an {@code Identifier}
			 *         into a {@code Path}
			 * @return the updated builder.
			 * @review
			 */
			public ResourceNameFunctionStep pathFunction(
				TriFunction<Identifier,
					Class<? extends Identifier>, Class<?>, Optional<Path>>
						pathFunction) {

				_pathFunction = pathFunction;

				return new ResourceNameFunctionStep();
			}

		}

		public class RepresentorFunctionStep {

			/**
			 * Add information to the builder about the function that can be
			 * used to obtain the {@code Representor} of a certain class.
			 *
			 * @param  representorFunction the function to obtain the {@code
			 *         Representor} of a class
			 * @return the updated builder.
			 * @review
			 */
			public RequestInfoStep representorFunction(
				Function<Class<?>,
					Optional<? extends Representor<?, ? extends Identifier>>>
						representorFunction) {

				_representorFunction = representorFunction;

				return new RequestInfoStep();
			}

		}

		public class RequestInfoStep {

			/**
			 * Add information about the request info to the builder.
			 *
			 * @param  requestInfo the information obtained from the request. It
			 *         can be created by using a {@link RequestInfo.Builder}
			 * @return the updated builder.
			 * @review
			 */
			public BuildStep requestInfo(RequestInfo requestInfo) {
				_requestInfo = requestInfo;

				return new BuildStep();
			}

		}

		public class ResourceNameFunctionStep {

			/**
			 * Add information to the builder about the function that can be
			 * used to obtain the name of the {@code Representor} of a certain
			 * class name.
			 *
			 * @param  resourceNameFunction the function to obtain the name of
			 *         the {@code Representor} of a certain class name
			 * @return the updated builder.
			 * @review
			 */
			public RepresentorFunctionStep resourceNameFunction(
				Function<String, Optional<String>> resourceNameFunction) {

				_resourceNameFunction = resourceNameFunction;

				return new RepresentorFunctionStep();
			}

		}

		private Page<T> _page;
		private PageMessageMapper<T> _pageMessageMapper;
		private TriFunction<Identifier,
			Class<? extends Identifier>, Class<?>, Optional<Path>>
				_pathFunction;
		private Function<Class<?>,
			Optional<? extends Representor<?, ? extends Identifier>>>
				_representorFunction;
		private RequestInfo _requestInfo;
		private Function<String, Optional<String>> _resourceNameFunction;

	}

	private Optional<String> _getCollectionURLOptional() {
		Path path = _page.getPath();

		Class<T> modelClass = _page.getModelClass();

		Optional<String> optional = _resourceNameFunction.apply(
			modelClass.getName());

		return optional.map(
			name -> createCollectionURL(
				_requestInfo.getServerURL(), path, name));
	}

	private <U> Optional<FieldsWriter<U, Identifier>> _getFieldsWriter(
		SingleModel<U> singleModel,
		FunctionalList<String> embeddedPathElements) {

		Optional<Representor<U, Identifier>> representorOptional =
			_getRepresentorOptional(singleModel.getModelClass());

		Optional<Path> pathOptional = _getPathOptional(singleModel);

		return representorOptional.flatMap(
			representor -> pathOptional.map(
				path -> new FieldsWriter<>(
					singleModel, _requestInfo, representor, path,
					embeddedPathElements)));
	}

	private <V> Optional<Path> _getPathOptional(SingleModel<V> singleModel) {
		Optional<Representor<V, Identifier>> optional = _getRepresentorOptional(
			singleModel.getModelClass());

		return optional.flatMap(
			representor -> _pathFunction.apply(
				representor.getIdentifier(singleModel.getModel()),
				representor.getIdentifierClass(), singleModel.getModelClass()));
	}

	@SuppressWarnings("unchecked")
	private <V, W extends Identifier> Optional<Representor<V, W>>
		_getRepresentorOptional(Class<V> modelClass) {

		Optional<? extends Representor<?, ? extends Identifier>> optional =
			_representorFunction.apply(modelClass);

		return optional.map(representor -> (Representor<V, W>)representor);
	}

	private void _writeItem(SingleModel<T> singleModel) {
		Optional<FieldsWriter<T, Identifier>> optional = _getFieldsWriter(
			singleModel, null);

		if (!optional.isPresent()) {
			return;
		}

		FieldsWriter<T, Identifier> fieldsWriter = optional.get();

		JSONObjectBuilder itemJsonObjectBuilder = new JSONObjectBuilder();

		_pageMessageMapper.onStartItem(
			_pageJsonObjectBuilder, itemJsonObjectBuilder,
			singleModel.getModel(), singleModel.getModelClass(),
			_requestInfo.getHttpHeaders());

		fieldsWriter.writeBooleanFields(
			(field, value) -> _pageMessageMapper.mapItemBooleanField(
				_pageJsonObjectBuilder, itemJsonObjectBuilder, field, value));

		fieldsWriter.writeLocalizedStringFields(
			(field, value) -> _pageMessageMapper.mapItemStringField(
				_pageJsonObjectBuilder, itemJsonObjectBuilder, field, value));

		fieldsWriter.writeNumberFields(
			(field, value) -> _pageMessageMapper.mapItemNumberField(
				_pageJsonObjectBuilder, itemJsonObjectBuilder, field, value));

		fieldsWriter.writeStringFields(
			(field, value) -> _pageMessageMapper.mapItemStringField(
				_pageJsonObjectBuilder, itemJsonObjectBuilder, field, value));

		fieldsWriter.writeLinks(
			(fieldName, link) -> _pageMessageMapper.mapItemLink(
				_pageJsonObjectBuilder, itemJsonObjectBuilder, fieldName,
				link));

		fieldsWriter.writeTypes(
			types -> _pageMessageMapper.mapItemTypes(
				_pageJsonObjectBuilder, itemJsonObjectBuilder, types));

		fieldsWriter.writeBinaries(
			(field, value) -> _pageMessageMapper.mapItemLink(
				_pageJsonObjectBuilder, itemJsonObjectBuilder, field, value));

		fieldsWriter.writeSingleURL(
			url -> _pageMessageMapper.mapItemSelfURL(
				_pageJsonObjectBuilder, itemJsonObjectBuilder, url));

		fieldsWriter.writeEmbeddedRelatedModels(
			this::_getPathOptional,
			(embeddedSingleModel, embeddedPathElements1) ->
				_writeItemEmbeddedModelFields(
					embeddedSingleModel, embeddedPathElements1,
					itemJsonObjectBuilder),
			(resourceURL, embeddedPathElements) ->
				_pageMessageMapper.mapItemLinkedResourceURL(
					_pageJsonObjectBuilder, itemJsonObjectBuilder,
					embeddedPathElements, resourceURL),
			(resourceURL, embeddedPathElements) ->
				_pageMessageMapper.mapItemEmbeddedResourceURL(
					_pageJsonObjectBuilder, itemJsonObjectBuilder,
					embeddedPathElements, resourceURL));

		fieldsWriter.writeLinkedRelatedModels(
			this::_getPathOptional,
			(url, embeddedPathElements) ->
				_pageMessageMapper.mapItemLinkedResourceURL(
					_pageJsonObjectBuilder, itemJsonObjectBuilder,
					embeddedPathElements, url));

		fieldsWriter.writeRelatedCollections(
			_resourceNameFunction,
			(url, embeddedPathElements) ->
				_pageMessageMapper.mapItemLinkedResourceURL(
					_pageJsonObjectBuilder, itemJsonObjectBuilder,
					embeddedPathElements, url));

		_pageMessageMapper.onFinishItem(
			_pageJsonObjectBuilder, itemJsonObjectBuilder,
			singleModel.getModel(), singleModel.getModelClass(),
			_requestInfo.getHttpHeaders());
	}

	private <V> void _writeItemEmbeddedModelFields(
		SingleModel<V> singleModel, FunctionalList<String> embeddedPathElements,
		JSONObjectBuilder itemJsonObjectBuilder) {

		Optional<FieldsWriter<V, Identifier>> optional = _getFieldsWriter(
			singleModel, embeddedPathElements);

		if (!optional.isPresent()) {
			return;
		}

		FieldsWriter<V, Identifier> fieldsWriter = optional.get();

		fieldsWriter.writeBooleanFields(
			(field, value) ->
				_pageMessageMapper.mapItemEmbeddedResourceBooleanField(
					_pageJsonObjectBuilder, itemJsonObjectBuilder,
					embeddedPathElements, field, value));

		fieldsWriter.writeLocalizedStringFields(
			(field, value) ->
				_pageMessageMapper.mapItemEmbeddedResourceStringField(
					_pageJsonObjectBuilder, itemJsonObjectBuilder,
					embeddedPathElements, field, value));

		fieldsWriter.writeNumberFields(
			(field, value) ->
				_pageMessageMapper.mapItemEmbeddedResourceNumberField(
					_pageJsonObjectBuilder, itemJsonObjectBuilder,
					embeddedPathElements, field, value));

		fieldsWriter.writeStringFields(
			(field, value) ->
				_pageMessageMapper.mapItemEmbeddedResourceStringField(
					_pageJsonObjectBuilder, itemJsonObjectBuilder,
					embeddedPathElements, field, value));

		fieldsWriter.writeLinks(
			(fieldName, link) -> _pageMessageMapper.mapItemEmbeddedResourceLink(
				_pageJsonObjectBuilder, itemJsonObjectBuilder,
				embeddedPathElements, fieldName, link));

		fieldsWriter.writeTypes(
			types -> _pageMessageMapper.mapItemEmbeddedResourceTypes(
				_pageJsonObjectBuilder, itemJsonObjectBuilder,
				embeddedPathElements, types));

		fieldsWriter.writeBinaries(
			(field, value) -> _pageMessageMapper.mapItemEmbeddedResourceLink(
				_pageJsonObjectBuilder, itemJsonObjectBuilder,
				embeddedPathElements, field, value));

		fieldsWriter.writeEmbeddedRelatedModels(
			this::_getPathOptional,
			(embeddedSingleModel, embeddedModelEmbeddedPathElements) ->
				_writeItemEmbeddedModelFields(
					embeddedSingleModel, embeddedModelEmbeddedPathElements,
					itemJsonObjectBuilder),
			(resourceURL, resourceEmbeddedPathElements) ->
				_pageMessageMapper.mapItemLinkedResourceURL(
					_pageJsonObjectBuilder, itemJsonObjectBuilder,
					resourceEmbeddedPathElements, resourceURL),
			(resourceURL, resourceEmbeddedPathElements) ->
				_pageMessageMapper.mapItemEmbeddedResourceURL(
					_pageJsonObjectBuilder, itemJsonObjectBuilder,
					resourceEmbeddedPathElements, resourceURL));

		fieldsWriter.writeLinkedRelatedModels(
			this::_getPathOptional,
			(url, resourceEmbeddedPathElements) ->
				_pageMessageMapper.mapItemLinkedResourceURL(
					_pageJsonObjectBuilder, itemJsonObjectBuilder,
					resourceEmbeddedPathElements, url));

		fieldsWriter.writeRelatedCollections(
			_resourceNameFunction,
			(url, resourceEmbeddedPathElements) ->
				_pageMessageMapper.mapItemLinkedResourceURL(
					_pageJsonObjectBuilder, itemJsonObjectBuilder,
					resourceEmbeddedPathElements, url));
	}

	private void _writePageURLs() {
		Optional<String> optional = _getCollectionURLOptional();

		optional.ifPresent(
			url -> {
				_pageMessageMapper.mapCurrentPageURL(
					_pageJsonObjectBuilder,
					createCollectionPageURL(url, _page, CURRENT));

				_pageMessageMapper.mapFirstPageURL(
					_pageJsonObjectBuilder,
					createCollectionPageURL(url, _page, FIRST));

				_pageMessageMapper.mapLastPageURL(
					_pageJsonObjectBuilder,
					createCollectionPageURL(url, _page, LAST));

				if (_page.hasNext()) {
					_pageMessageMapper.mapNextPageURL(
						_pageJsonObjectBuilder,
						createCollectionPageURL(url, _page, NEXT));
				}

				if (_page.hasPrevious()) {
					_pageMessageMapper.mapPreviousPageURL(
						_pageJsonObjectBuilder,
						createCollectionPageURL(url, _page, PREVIOUS));
				}
			});
	}

	private final Page<T> _page;
	private final JSONObjectBuilder _pageJsonObjectBuilder;
	private final PageMessageMapper<T> _pageMessageMapper;
	private final TriFunction<Identifier,
		Class<? extends Identifier>, Class<?>, Optional<Path>> _pathFunction;
	private final Function<Class<?>,
		Optional<? extends Representor<?, ? extends Identifier>>>
			_representorFunction;
	private final RequestInfo _requestInfo;
	private final Function<String, Optional<String>> _resourceNameFunction;

}