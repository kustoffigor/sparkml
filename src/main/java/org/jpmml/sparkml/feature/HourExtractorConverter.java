package org.jpmml.sparkml.feature;


import com.antifraud.HourExtractor;
import com.antifraud.MinuteExtractor;
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

public class HourExtractorConverter extends FeatureConverter<HourExtractor> {

    public HourExtractorConverter(HourExtractor transformer) {
        super(transformer);
    }

    @Override
    public List<Feature> encodeFeatures(FeatureMapper featureMapper){
        HourExtractor transformer = getTransformer();
        Feature inputFeature = featureMapper.getOnlyFeature(transformer.getInputCol());
        Apply apply = PMMLUtil.createApply("formatDateTime", inputFeature.ref(), PMMLUtil.createConstant("%H"));
        DerivedField derivedField = featureMapper.createDerivedField(formatName(transformer), OpType.ORDINAL, DataType.INTEGER, apply);
        Feature feature = new ContinuousFeature(derivedField);
        return Collections.singletonList(feature);
    }
}