/*
 * Copyright (c) 2016 Villu Ruusmann
 *
 * This file is part of JPMML-SparkML
 *
 * JPMML-SparkML is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-SparkML is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-SparkML.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jpmml.sparkml;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.bind.JAXBException;

import com.antifraud.*;
import com.google.common.collect.Iterables;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.spark.ml.PipelineModel;
import org.apache.spark.ml.Transformer;
import org.apache.spark.ml.classification.DecisionTreeClassificationModel;
import org.apache.spark.ml.classification.GBTClassificationModel;
import org.apache.spark.ml.classification.LogisticRegressionModel;
import org.apache.spark.ml.classification.MultilayerPerceptronClassificationModel;
import org.apache.spark.ml.classification.RandomForestClassificationModel;
import org.apache.spark.ml.clustering.KMeansModel;
import org.apache.spark.ml.feature.Binarizer;
import org.apache.spark.ml.feature.Bucketizer;
import org.apache.spark.ml.feature.ChiSqSelectorModel;
import org.apache.spark.ml.feature.ColumnPruner;
import org.apache.spark.ml.feature.IndexToString;
import org.apache.spark.ml.feature.MinMaxScalerModel;
import org.apache.spark.ml.feature.OneHotEncoder;
import org.apache.spark.ml.feature.PCAModel;
import org.apache.spark.ml.feature.RFormulaModel;
import org.apache.spark.ml.feature.StandardScalerModel;
import org.apache.spark.ml.feature.StringIndexerModel;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.ml.feature.VectorAttributeRewriter;
import org.apache.spark.ml.feature.VectorIndexerModel;
import org.apache.spark.ml.feature.VectorSlicer;
import org.apache.spark.ml.param.shared.HasPredictionCol;
import org.apache.spark.ml.regression.DecisionTreeRegressionModel;
import org.apache.spark.ml.regression.GBTRegressionModel;
import org.apache.spark.ml.regression.GeneralizedLinearRegressionModel;
import org.apache.spark.ml.regression.LinearRegressionModel;
import org.apache.spark.ml.regression.RandomForestRegressionModel;
import org.apache.spark.sql.types.StructType;
import org.dmg.pmml.DataField;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningField;
import org.dmg.pmml.MiningSchema;
import org.dmg.pmml.Output;
import org.dmg.pmml.OutputField;
import org.dmg.pmml.PMML;
import org.dmg.pmml.ResultFeature;
import org.dmg.pmml.mining.MiningModel;
import org.jpmml.converter.Schema;
import org.jpmml.converter.mining.MiningModelUtil;
import org.jpmml.model.MetroJAXBUtil;
import org.jpmml.sparkml.feature.*;
import org.jpmml.sparkml.model.DecisionTreeClassificationModelConverter;
import org.jpmml.sparkml.model.DecisionTreeRegressionModelConverter;
import org.jpmml.sparkml.model.GBTClassificationModelConverter;
import org.jpmml.sparkml.model.GBTRegressionModelConverter;
import org.jpmml.sparkml.model.GeneralizedLinearRegressionModelConverter;
import org.jpmml.sparkml.model.KMeansModelConverter;
import org.jpmml.sparkml.model.LinearRegressionModelConverter;
import org.jpmml.sparkml.model.LogisticRegressionModelConverter;
import org.jpmml.sparkml.model.MultilayerPerceptronClassificationModelConverter;
import org.jpmml.sparkml.model.RandomForestClassificationModelConverter;
import org.jpmml.sparkml.model.RandomForestRegressionModelConverter;

public class ConverterUtil {

	private ConverterUtil(){
	}

	static
	public PMML toPMML(StructType schema, PipelineModel pipelineModel){
		FeatureMapper featureMapper = new FeatureMapper(schema);

		Map<String, org.dmg.pmml.Model> models = new LinkedHashMap<>();

		Transformer[] stages = pipelineModel.stages();
		for(Transformer stage : stages){
			TransformerConverter<?> converter = ConverterUtil.createConverter(stage);

			if(converter instanceof FeatureConverter){
				FeatureConverter<?> featureConverter = (FeatureConverter<?>)converter;

				featureMapper.append(featureConverter);
			} else

			if(converter instanceof ModelConverter){
				ModelConverter<?> modelConverter = (ModelConverter<?>)converter;

				Schema featureSchema = featureMapper.createSchema(modelConverter);

				org.dmg.pmml.Model model = modelConverter.encodeModel(featureSchema);

				featureMapper.append(modelConverter);

				HasPredictionCol hasPredictionCol = (HasPredictionCol)stage;

				models.put(hasPredictionCol.getPredictionCol(), model);
			} else

			{
				throw new IllegalArgumentException();
			}
		}

		org.dmg.pmml.Model rootModel;

		if(models.size() == 1){
			rootModel = Iterables.getOnlyElement(models.values());
		} else

		if(models.size() >= 2){
			List<MiningField> targetMiningFields = new ArrayList<>();

			List<Map.Entry<String, org.dmg.pmml.Model>> entries = new ArrayList<>(models.entrySet());
			for(Iterator<Map.Entry<String, org.dmg.pmml.Model>> entryIt = entries.iterator(); entryIt.hasNext(); ){
				Map.Entry<String, org.dmg.pmml.Model> entry = entryIt.next();

				String predictionCol = entry.getKey();
				org.dmg.pmml.Model model = entry.getValue();

				MiningSchema miningSchema = model.getMiningSchema();

				List<MiningField> miningFields = miningSchema.getMiningFields();
				for(Iterator<MiningField> miningFieldIt = miningFields.iterator(); miningFieldIt.hasNext(); ){
					MiningField miningField = miningFieldIt.next();

					MiningField.UsageType usageType = miningField.getUsageType();
					switch(usageType){
						case PREDICTED:
						case TARGET:
							targetMiningFields.add(miningField);
							break;
						default:
							break;
					}
				}

				if(!entryIt.hasNext()){
					break;
				}

				FieldName name = FieldName.create(predictionCol);

				DataField dataField = featureMapper.getDataField(name);
				if(dataField == null){
					throw new IllegalArgumentException();
				}

				featureMapper.removeDataField(name);

				Output output = model.getOutput();
				if(output == null){
					output = new Output();

					model.setOutput(output);
				}

				OutputField outputField = new OutputField(name, dataField.getDataType())
					.setOpType(dataField.getOpType())
					.setResultFeature(ResultFeature.PREDICTED_VALUE);

				output.addOutputFields(outputField);
			}

			MiningSchema miningSchema = new MiningSchema(targetMiningFields);

			List<org.dmg.pmml.Model> memberModels = new ArrayList<>(models.values());

			MiningModel miningModel = MiningModelUtil.createModelChain(null, Collections.<FieldName>emptyList(), memberModels)
				.setMiningSchema(miningSchema);

			rootModel = miningModel;
		} else

		{
			throw new IllegalArgumentException();
		}

		PMML pmml = featureMapper.encodePMML(rootModel);

		return pmml;
	}

	static
	public byte[] toPMMLByteArray(StructType schema, PipelineModel pipelineModel){
		PMML pmml = toPMML(schema, pipelineModel);

		ByteArrayOutputStream os = new ByteArrayOutputStream(1024 * 1024);

		try {
			MetroJAXBUtil.marshalPMML(pmml, os);
		} catch(JAXBException je){
			throw new RuntimeException(je);
		}

		return os.toByteArray();
	}

	static
	public FeatureConverter<?> createFeatureConverter(Transformer transformer){
		return (FeatureConverter<?>)createConverter(transformer);
	}

	static
	public ModelConverter<?> createModelConverter(Transformer transformer){
		return (ModelConverter<?>)createConverter(transformer);
	}

	static
	public <T extends Transformer> TransformerConverter<T> createConverter(T transformer){
		Class<? extends Transformer> clazz = transformer.getClass();

		Class<? extends TransformerConverter> converterClazz = getConverterClazz(clazz);
		if(converterClazz == null){
			throw new IllegalArgumentException("Transformer class " + clazz.getName() + " is not supported");
		}

		try {
			Constructor<?> constructor = converterClazz.getDeclaredConstructor(clazz);

			return (TransformerConverter)constructor.newInstance(transformer);
		} catch(Exception e){
			throw new IllegalArgumentException(e);
		}
	}

	static
	public Class<? extends TransformerConverter> getConverterClazz(Class<? extends Transformer> clazz){
		return ConverterUtil.converters.get(clazz);
	}

	static
	public void putConverterClazz(Class<? extends Transformer> clazz, Class<? extends TransformerConverter<?>> converterClazz){
		ConverterUtil.converters.put(clazz, converterClazz);
	}

	private static final Map<Class<? extends Transformer>, Class<? extends TransformerConverter>> converters = new LinkedHashMap<>();

	static {
		// Features
		converters.put(Binarizer.class, BinarizerConverter.class);
		converters.put(Bucketizer.class, BucketizerConverter.class);
		converters.put(ChiSqSelectorModel.class, ChiSqSelectorModelConverter.class);
		converters.put(ColumnPruner.class, ColumnPrunerConverter.class);
		converters.put(IndexToString.class, IndexToStringConverter.class);
		converters.put(MinMaxScalerModel.class, MinMaxScalerModelConverter.class);
		converters.put(OneHotEncoder.class, OneHotEncoderConverter.class);
		converters.put(PCAModel.class, PCAModelConverter.class);
		converters.put(RFormulaModel.class, RFormulaModelConverter.class);
		converters.put(StandardScalerModel.class, StandardScalerModelConverter.class);
		converters.put(StringIndexerModel.class, StringIndexerModelConverter.class);
		converters.put(VectorAssembler.class, VectorAssemblerConverter.class);
		converters.put(VectorAttributeRewriter.class, VectorAttributeRewriterConverter.class);
		converters.put(VectorIndexerModel.class, VectorIndexerModelConverter.class);
		converters.put(VectorSlicer.class, VectorSlicerConverter.class);
		converters.put(YearExtractor.class, YearExtractorConverter.class);
		converters.put(MonthExtractor.class, MonthExtractorConverter.class);
		converters.put(DayExtractor.class, DayExtractorConverter.class);
		converters.put(HourExtractor.class, HourExtractorConverter.class);
		converters.put(MinuteExtractor.class, MinuteExtractorConverter.class);

		// Models
		converters.put(DecisionTreeClassificationModel.class, DecisionTreeClassificationModelConverter.class);
		converters.put(DecisionTreeRegressionModel.class, DecisionTreeRegressionModelConverter.class);
		converters.put(GBTClassificationModel.class, GBTClassificationModelConverter.class);
		converters.put(GBTRegressionModel.class, GBTRegressionModelConverter.class);
		converters.put(GeneralizedLinearRegressionModel.class, GeneralizedLinearRegressionModelConverter.class);
		converters.put(KMeansModel.class, KMeansModelConverter.class);
		converters.put(LinearRegressionModel.class, LinearRegressionModelConverter.class);
		converters.put(LogisticRegressionModel.class, LogisticRegressionModelConverter.class);
		converters.put(MultilayerPerceptronClassificationModel.class, MultilayerPerceptronClassificationModelConverter.class);
		converters.put(RandomForestClassificationModel.class, RandomForestClassificationModelConverter.class);
		converters.put(RandomForestRegressionModel.class, RandomForestRegressionModelConverter.class);
	}
}