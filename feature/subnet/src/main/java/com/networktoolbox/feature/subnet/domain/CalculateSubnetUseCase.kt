package com.networktoolbox.feature.subnet.domain

import com.networktoolbox.core.common.ipv4.SubnetCalculator
import com.networktoolbox.core.common.ipv4.SubnetResult
import javax.inject.Inject

class CalculateSubnetUseCase @Inject constructor() {
    operator fun invoke(input: String): Result<SubnetResult> = runCatching {
        SubnetCalculator.calculate(input)
    }
}
