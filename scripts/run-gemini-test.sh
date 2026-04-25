#!/bin/bash
# Helper script to run GeminiRealApiTests.testListModels on desktop
# Workaround for --tests flag not working with Kotlin Multiplatform

cd "$(dirname "$0")/.."

echo "Running all desktop tests (GeminiRealApiTests.testListModels will execute)..."
echo ""

# Run all desktop tests with --continue so it doesn't fail on other test failures
# The Gemini tests will run and you can see their output
./gradlew :shared:desktopTest --continue 2>&1 | \
  tee /tmp/desktop-test-output.txt
  
echo ""
echo "=== Test Summary ==="
grep -E "(tests completed|GeminiRealApiTests)" /tmp/desktop-test-output.txt | tail -3

echo ""
echo "=== Looking for testListModels output ==="
if grep -q "testListModels\|ListModels Response" /tmp/desktop-test-output.txt; then
    grep -A 10 "testListModels\|ListModels Response" /tmp/desktop-test-output.txt | head -20
else
    echo "Test output saved to /tmp/desktop-test-output.txt"
    echo "Run: cat /tmp/desktop-test-output.txt | grep -i gemini"
fi
