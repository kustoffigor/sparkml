package org.jpmml.sparkml.feature;

import com.antifraud.MonthExtractor;
import org.dmg.pmml.Apply;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.OpType;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.sparkml.FeatureConverter;
import org.jpmml.sparkml.FeatureMapper;

import java.util.Collections;
import java.util.List;

public class MonthExtractorConverter extends FeatureConverter<MonthExtractor> {

    public MonthExtractorConverter(MonthExtractor transformer) {
        super(transformer);
    }

    @Override
    public List<Feature> encodeFeatures(FeatureMapper featureMapper){
        MonthExtractor transformer = getTransformer();
        Feature inputFeature = featureMapper.getOnlyFeature(transformer.getInputCol());
        Apply apply = PMMLUtil.createApply("formatDateTime", inputFeature.ref(), PMMLUtil.createConstant("%m"));
        DerivedField derivedField = featureMapper.createDerivedField(formatName(transformer), OpType.ORDINAL, DataType.INTEGER, apply);
        Feature feature = new ContinuousFeature(derivedField);
        return Collections.singletonList(feature);
    }
}