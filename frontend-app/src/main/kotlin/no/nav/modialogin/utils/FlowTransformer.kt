package no.nav.modialogin.utils

import kotlinx.coroutines.flow.*
import kotlinx.serialization.KSerializer
import no.nav.modialogin.utils.Encoding.decode

object FlowTransformer {
    fun <DATA_TYPE, OUTPUT_TYPE>mapData(inputFlow: Flow<String>, dataSerializer: KSerializer<DATA_TYPE>, transformFunction: (DATA_TYPE) -> OUTPUT_TYPE): Flow<OUTPUT_TYPE> {
        return inputFlow.map {
            val decodedData = decode(dataSerializer, it)
            transformFunction(decodedData)
        }
    }
}
