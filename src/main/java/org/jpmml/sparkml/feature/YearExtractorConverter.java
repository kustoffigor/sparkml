package org.jpmml.sparkml.feature;

import org.dmg.pmml.Apply;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.OpType;
import org.jpmml.converter.ContinuousFeature;
import org.jpmml.converter.Feature;
import org.jpmml.converter.PMMLUtil;
import org.jpmml.sparkml.FeatureConverter;
import com.antifraud.YearExtractor;
import org.jpmml.sparkml.FeatureMapper;

import java.util.Collections;
import java.util.List;

public class YearExtractorConverter extends FeatureConverter<YearExtractor> {

    public YearExtractorConverter(YearExtractor transformer) {
        super(transformer);
    }



    @Override
    public List<Feature> encodeFeatures(FeatureMapper featureMapper){
        YearExtractor transformer = getTransformer();
        Feature inputFeature = featureMapper.getOnlyFeature(transformer.getInputCol());
        Apply apply = PMMLUtil.createApply("formatDateTime", inputFeature.ref(), PMMLUtil.createConstant("%y"));
        DerivedField derivedField = featureMapper.createDerivedField(formatName(transformer), OpType.ORDINAL, DataType.INTEGER, apply);
        Feature feature = new ContinuousFeature(derivedField);
        return Collections.singletonList(feature);
    }
}
