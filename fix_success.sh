sed -i '1559,1570c\
            is OtpDeliveryResult.Success -> {\
                _otpUiState.value = OtpUiState.Success(deliveryResult.message, deliveryResult.details)\
                withContext(Dispatchers.Main) {\
                    try { Toast.makeText(getApplication(), "OTP sent to $cleanEmail", Toast.LENGTH_LONG).show() } catch (e: Throwable) {}\
                }\
                NetlifyOtpResult.Success(deliveryResult.message, activeClientOtp)\
            }' app/src/main/java/com/example/viewmodel/PlenxoViewModel.kt
