package com.mgmtp.a12.kernel.mmvalidators;

import com.mgmtp.a12.kernel.core.rt.a12internal.meta.IMetaModel;
import com.mgmtp.a12.kernel.core.rt.a12internal.validation.IValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TestValidators {

    @Test
    void jarContainsFactoryForPublicDV() {
        // compile-time FQN + minimal runtime touch: obtain INSTANCE and access meta/validator
        // NOTE: keep fqn here, don't replace by import. It should be as explicit as possible because we want to test that the fqn is as expected, e.g. that it contains ".extmpinternal."
        var f = com.mgmtp.a12.kernel.mmvalidators.extmpinternal.a12_mm_selectionmodel__0032.Factory_a12_mm_selectionmodel__0032.getInstance();
        assertNotNull(f);
        IMetaModel meta = f.getMetaObject();
        assertNotNull(meta);
        IValidator validator = f.getValidator();
        assertNotNull(validator);
    }

    @Test
    void jarContainsFactoryForNonPublicDV() {
        // compile-time FQN + minimal runtime touch
        // NOTE: keep fqn here, don't replace by import. It should be as explicit as possible because we want to test that the fqn is as expected, e.g. that it contains ".extmpinternal."
        var f = com.mgmtp.a12.kernel.mmvalidators.extmpinternal.a12_mm_smm_fieldtypechecks__0031.Factory_a12_mm_smm_fieldtypechecks__0031.getInstance();
        assertNotNull(f);
        IMetaModel meta = f.getMetaObject();
        assertNotNull(meta);
        IValidator validator = f.getValidator();
        assertNotNull(validator);
    }

    @Test
    void validatorsForSmeModelStayExtmpinternal() {
        // MM_MappingModel-2 has a12internal dmjsons for SME, but validators must still be extmpinternal
        // NOTE: keep fqn here, don't replace by import. It should be as explicit as possible because we want to test that the fqn is as expected, e.g. that it contains ".extmpinternal."
        var f = com.mgmtp.a12.kernel.mmvalidators.extmpinternal.a12_mm_mappingmodel__0032.Factory_a12_mm_mappingmodel__0032.getInstance();
        assertNotNull(f);
        IMetaModel meta = f.getMetaObject();
        assertNotNull(meta);
        IValidator validator = f.getValidator();
        assertNotNull(validator);
    }

}
