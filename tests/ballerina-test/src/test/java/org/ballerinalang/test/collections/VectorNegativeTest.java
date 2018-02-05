/*
 * Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinalang.test.collections;

import org.ballerinalang.launcher.util.BCompileUtil;
import org.ballerinalang.launcher.util.BRunUtil;
import org.ballerinalang.launcher.util.CompileResult;
import org.ballerinalang.model.values.BIntArray;
import org.ballerinalang.model.values.BInteger;
import org.ballerinalang.model.values.BRefValueArray;
import org.ballerinalang.model.values.BStruct;
import org.ballerinalang.model.values.BValue;
import org.testng.Assert;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

/**
 * Negative test cases for Vectors
 */
public class VectorNegativeTest {

    private CompileResult compileResult;

    @BeforeTest
    public void setup() {
        compileResult = BCompileUtil.compile("test-src/collections/vector-test.bal");
    }

    @Test(description = "Test case for testing retrieval of elements from outside the bounds of a vector.")
    public void testGetIndexOutOfRange() {
        long[] expectedVals = new long[]{10, 30};
        long vectorSize = 10;
        String[] expectedErrMsgs = new String[]{"Index out of bounds: 15", "Index out of bounds: -1"};

        BValue[] returns = BRunUtil.invoke(compileResult, "testGetIndexOutOfRange",
                                           new BValue[]{new BInteger(vectorSize)});

        Assert.assertNotNull(returns);

        Assert.assertEquals(((BInteger) returns[0]).intValue(), expectedVals[0]);
        Assert.assertEquals(((BInteger) returns[1]).intValue(), expectedVals[1]);

        Assert.assertNull(returns[2]);
        Assert.assertNull(returns[3]);

        Assert.assertEquals(((BStruct) returns[4]).getStringField(0), expectedErrMsgs[0]);
        Assert.assertEquals(((BStruct) returns[5]).getStringField(0), expectedErrMsgs[1]);
    }

    @Test(description = "Test case for testing insertion of elements outside the bounds of a vector.")
    public void testInsertIndexOutOfRange() {
        long[] values = new long[]{100, 110, 120};
        long[] indices = new long[]{3, 20, -1}; // 1 valid index and 2 invalid indices
        int vectorSize = 10;
        long[] expectedFinalValues = new long[]{10, 20, 30, 100, 40, 50, 60, 70, 80, 90, 100};
        String[] expectedErrMsgs = new String[]{"Index out of bounds: 20", "Index out of bounds: -1"};

        BValue[] returns = BRunUtil.invoke(compileResult, "testInsertIndexOutOfRange",
                                           new BValue[]{buildIntArray(values), buildIntArray(indices),
                                                   new BInteger(vectorSize)});

        Assert.assertNotNull(returns);

        BStruct vector = (BStruct) returns[0];
        BRefValueArray vectorEntries = (BRefValueArray) vector.getRefField(0);
        long finalVectorSize = vector.getIntField(0);

        Assert.assertEquals(finalVectorSize, vectorSize + 1);

        for (int i = 0; i < expectedFinalValues.length; i++) {
            Assert.assertEquals(vectorEntries.get(i).value(), expectedFinalValues[i]);
        }

        BRefValueArray errors = (BRefValueArray) returns[1];

        Assert.assertEquals(errors.size(), 3); // Since there are 3 insertions
        Assert.assertNull(errors.get(0)); // Since first insertion is valid, the error is null
        Assert.assertEquals(((BStruct) errors.get(1)).getStringField(0), expectedErrMsgs[0]);
        Assert.assertEquals(((BStruct) errors.get(2)).getStringField(0), expectedErrMsgs[1]);
    }

    @Test(description = "Test case for testing removal of elements from outside of the bounds of a vector.")
    public void testRemoveIndexOutOfRange() {
        long removedVal = 10;
        long vectorSize = 10;
        long[] expectedFinalValues = new long[]{20, 30, 40, 50, 60, 70, 80, 90, 100};
        String[] expectedErrMsgs = new String[]{"Index out of bounds: 20", "Index out of bounds: -1"};

        BValue[] returns = BRunUtil.invoke(compileResult, "testRemoveIndexOutOfRange",
                                           new BValue[]{new BInteger(vectorSize)});

        Assert.assertNotNull(returns);

        BStruct vector = (BStruct) returns[0];
        BRefValueArray vectorEntries = (BRefValueArray) vector.getRefField(0);
        long finalVectorSize = vector.getIntField(0);

        Assert.assertEquals(finalVectorSize, expectedFinalValues.length);

        for (int i = 0; i < finalVectorSize; i++) {
            Assert.assertEquals(vectorEntries.get(i).value(), expectedFinalValues[i]);
        }

        Assert.assertEquals(((BInteger) returns[1]).intValue(), removedVal);
        Assert.assertNull(returns[2]);
        Assert.assertNull(returns[3]);

        BRefValueArray errors = (BRefValueArray) returns[4];

        Assert.assertEquals(errors.size(), 3); // Since there are 3 removals
        Assert.assertNull(errors.get(0)); // Since first removal is valid, the error is null
        Assert.assertEquals(((BStruct) errors.get(1)).getStringField(0), expectedErrMsgs[0]);
        Assert.assertEquals(((BStruct) errors.get(2)).getStringField(0), expectedErrMsgs[1]);
    }

    private BIntArray buildIntArray(long[] args) {
        BIntArray valueArray = new BIntArray();

        for (int i = 0; i < args.length; i++) {
            valueArray.add(i, args[i]);
        }

        return valueArray;
    }
}
