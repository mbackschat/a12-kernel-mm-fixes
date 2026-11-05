package com.mgmtp.a12.kernel.mmtypings;

// @formatter:off // disable auto formatter, because it could break a12.os.remove statements
import com.mgmtp.a12.kernel.mmtypings.mm_mappingmodel_2.views._mm_mappingmodel_2._content.MappingType;
import com.mgmtp.a12.kernel.mmtypings.mm_structuralmappingmodel_1.views.MM_StructuralMappingModel_1;
import com.mgmtp.a12.kernel.mmtypings.mm_structuralmappingmodel_1.views._mm_structuralmappingmodel_1._content._mappingblocks._resolutionstrategies.Type;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.mgmtp.a12.kernel.mmtypings.mm_mappingmodel_2.views.MM_MappingModel_2;
import com.mgmtp.a12.kernel.mmtypings.mm_combinationmodel_1.views.MM_CombinationModel_1;
import com.mgmtp.a12.kernel.mmtypings.mm_selectionmodel_2.views.MM_SelectionModel_2;
import com.mgmtp.a12.kernel.mmtypings.ct_selection_v1.views._ct_selection_v1.Selection;
import com.mgmtp.a12.kernel.mmtypings.ct_selection_v1.views._ct_selection_v1._selection.Escaped_Default;
import com.mgmtp.a12.kernel.md.document.apiV2.immutable.DocumentV2;
// @formatter:off // disable auto-formatter again, as optimize imports might remove setting from above

public class TestApiTypings {
    @Test void headerClassesAreShared() {
        // the interesting checks happen already during compilation. executing this test is mainly done because
        // it seems like the easiest and most robust way to make sure the compilation succeeded; and as some sort of
        // limited extra general sanity check
        var pHeaderMapping = MM_MappingModel_2._pointer().header();
        MM_MappingModel_2 mappingModel = MM_MappingModel_2._empty()._with(
                pHeaderMapping.id(), "someId"
        );

        MM_CombinationModel_1 cm = MM_CombinationModel_1._empty()._with(
                MM_CombinationModel_1._pointer().header(), mappingModel._at(pHeaderMapping)
        );

        // also typeckeck header of selection model 2
        MM_SelectionModel_2 sm = MM_SelectionModel_2._empty()._with(
                MM_SelectionModel_2._pointer().header(), mappingModel._at(pHeaderMapping)
        );

        // NOTE: keep the fqn, we want to confirm that the header class is in the expected package
        com.mgmtp.a12.kernel.mmtypings.ct_model_header_1.views._ct_model_header_1.Header cmHeader = cm.header();

        assertEquals(
                DocumentV2.empty("MM_CombinationModel_1").withFieldValue("/header/id", "someId").group("/header"),
                cmHeader._unwrap()
        );
    }

    @Test
    void selectionSharesTypeForDataAndValidationSubgroups() {
        // selection v2: should share classes for data, validation and computation
        var pSel = MM_SelectionModel_2._pointer();
        MM_SelectionModel_2 sel2 = MM_SelectionModel_2._empty()
                ._with(pSel.content().data().escaped_Default(), Escaped_Default.Selected);
        Selection dataSelection = sel2._at(pSel.content().data());
        // check that the compiler recognizes the classes as shared
        sel2 = sel2
                ._with(pSel.content().validation(), dataSelection)
                ._with(pSel.content().computation(), dataSelection);

        // some runtime assertion as a sanity check
        assertEquals(
                DocumentV2.empty("MM_SelectionModel_2")
                        .withFieldValue("content/Data/Default", "Selected")
                        .withFieldValue("content/Validation/Default", "Selected")
                        .withFieldValue("content/Computation/Default", "Selected"),
                sel2._unwrap()
        );

    }

    @Test
    void testGroupsOfFieldsThatDontOccurInCurrentVariantAreRemoved() {
        // Public typings class should NOT expose rEPO_DATA()
        String repoData_methodName = "rEPO_DATA";
        boolean publicHasRepoData = java.util.Arrays.stream(MM_MappingModel_2.class.getMethods())
                .anyMatch(m -> m.getName().equals(repoData_methodName));
        assertEquals(false, publicHasRepoData);

        // Internal "none" variant typings should expose rEPO_DATA()
        boolean noneHasRepoData = java.util.Arrays.stream(
                        com.mgmtp.a12.kernel.mmtypings.extmpinternal.mm_mappingmodel_2_none.views.MM_MappingModel_2.class.getMethods())
                .anyMatch(m -> m.getName().equals(repoData_methodName));
        assertEquals(true, noneHasRepoData);
    }

    // temporary test, can be removed after the 31.x kernel release
    @Test
    void testIs31OrLaterTypingsGenerator() {
        MM_MappingModel_2 mm = MM_MappingModel_2._empty()
                ._with(MM_MappingModel_2._pointer().content().mappingType(), MappingType.De_DE.import_A); // enum class for Nichtabgeschlossene Enumeration field

        assertEquals("import_A", mm._unwrap().fieldValue(MM_MappingModel_2._pointer().content().mappingType()._unwrap()));

        MM_StructuralMappingModel_1 smm = MM_StructuralMappingModel_1._empty()
                ._with(MM_StructuralMappingModel_1._pointer().content().mappingBlocks(1).resolutionStrategies(1).type(),
                        Type.Slice); // check that Type is now capitalized
    }

}