/*
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 * Use of this source code is governed by the Apache 2.0 license that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.analysis.api.fir.components

import org.jetbrains.kotlin.analysis.api.components.KtImplicitReceiverSmartCast
import org.jetbrains.kotlin.analysis.api.components.KtImplicitReceiverSmartCastKind
import org.jetbrains.kotlin.analysis.api.components.KtSmartCastInfo
import org.jetbrains.kotlin.analysis.api.components.KtSmartCastProvider
import org.jetbrains.kotlin.analysis.api.fir.KtFirAnalysisSession
import org.jetbrains.kotlin.analysis.api.tokens.ValidityToken
import org.jetbrains.kotlin.analysis.api.withValidityAssertion
import org.jetbrains.kotlin.analysis.low.level.api.fir.api.getOrBuildFir
import org.jetbrains.kotlin.fir.expressions.*
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.coneTypeSafe
import org.jetbrains.kotlin.fir.types.isStableSmartcast
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.psi.psiUtil.getQualifiedExpressionForSelectorOrThis

internal class KtFirSmartcastProvider(
    override val analysisSession: KtFirAnalysisSession,
    override val token: ValidityToken,
) : KtSmartCastProvider(), KtFirAnalysisSessionComponent {

    private fun KtExpression.getPossiblyQualifiedExpressionForNameReference(): KtExpression? =
        when (this) {
            is KtNameReferenceExpression -> {
                val possibleCall = this.parent as? KtCallExpression ?: this
                possibleCall.getQualifiedExpressionForSelectorOrThis()
            }
            else -> null
        }

    private fun KtExpression.getMatchingSmartCastExpression() =
        when (val firExpression = this.getOrBuildFir(analysisSession.firResolveState)) {
            is FirExpressionWithSmartcast -> firExpression
            is FirImplicitInvokeCall -> firExpression.explicitReceiver as? FirExpressionWithSmartcast
            else -> null
        }

    override fun getSmartCastedInfo(expression: KtExpression): KtSmartCastInfo? = withValidityAssertion {
        val wholePsiExpression = expression.getPossiblyQualifiedExpressionForNameReference()

        val firSmartCastExpression = wholePsiExpression?.getMatchingSmartCastExpression() ?: return null

        getSmartCastedInfo(firSmartCastExpression)
    }

    private fun getSmartCastedInfo(expression: FirExpressionWithSmartcast): KtSmartCastInfo? {
        val type = expression.smartcastType.coneTypeSafe<ConeKotlinType>()?.asKtType() ?: return null
        return KtSmartCastInfo(type, expression.isStable, token)
    }

    private fun KtExpression.getOperationExpressionForOperation(): KtOperationExpression? =
        (this as? KtOperationReferenceExpression)?.parent as? KtOperationExpression

    private fun KtExpression.getMatchingFirQualifiedAccessExpression(): FirQualifiedAccessExpression? =
        when (val firExpression = this.getOrBuildFir(analysisSession.firResolveState)) {
            is FirQualifiedAccessExpression -> firExpression
            is FirSafeCallExpression -> firExpression.selector as? FirQualifiedAccessExpression
            else -> null
        }

    override fun getImplicitReceiverSmartCast(expression: KtExpression): Collection<KtImplicitReceiverSmartCast> = withValidityAssertion {
        val wholePsiExpression = expression.getPossiblyQualifiedExpressionForNameReference()
            ?: expression.getOperationExpressionForOperation()

        val firQualifiedExpression = wholePsiExpression?.getMatchingFirQualifiedAccessExpression() ?: return emptyList()

        listOfNotNull(
            smartCastedImplicitReceiver(firQualifiedExpression, KtImplicitReceiverSmartCastKind.DISPATCH),
            smartCastedImplicitReceiver(firQualifiedExpression, KtImplicitReceiverSmartCastKind.EXTENSION),
        )
    }

    private fun smartCastedImplicitReceiver(
        firExpression: FirQualifiedAccessExpression,
        kind: KtImplicitReceiverSmartCastKind,
    ): KtImplicitReceiverSmartCast? {
        val receiver = when (kind) {
            KtImplicitReceiverSmartCastKind.DISPATCH -> firExpression.dispatchReceiver
            KtImplicitReceiverSmartCastKind.EXTENSION -> firExpression.extensionReceiver
        }

        if (receiver == firExpression.explicitReceiver) return null
        if (!receiver.isStableSmartcast()) return null

        val type = receiver.typeRef.coneTypeSafe<ConeKotlinType>()?.asKtType() ?: return null
        return KtImplicitReceiverSmartCast(type, kind, token)
    }
}