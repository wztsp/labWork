$repo = "$env:USERPROFILE\.m2\repository"
$cp = @()
$cp += "d:\traeitem\labWork\LIS-clinical-lab-solution\lis-system\lis-common\target\classes"
$cp += "d:\traeitem\labWork\LIS-clinical-lab-solution\lis-system\lis-api\target\classes"
$cp += "d:\traeitem\labWork\LIS-clinical-lab-solution\lis-system\lis-service\lis-service-integration\target\classes"
$cp += "$repo\org\springframework\boot\*"
$cp += "$repo\org\springframework\boot\spring-boot-starter-web\3.2.0\*"
$cp += "$repo\org\springframework\boot\spring-boot-starter-amqp\3.2.0\*"
$cp += "$repo\org\springframework\boot\spring-boot-starter-data-redis\3.2.0\*"
$cp += "$repo\org\springframework\spring-web\6.1.1\*"
$cp += "$repo\org\springframework\spring-context\6.1.1\*"
$cp += "$repo\org\springframework\spring-beans\6.1.1\*"
$cp += "$repo\org\springframework\spring-core\6.1.1\*"
$cp += "$repo\com\fasterxml\jackson\core\*"
$cp += "$repo\com\fasterxml\jackson\core\jackson-databind\2.15.3\*"
$cp += "$repo\com\fasterxml\jackson\core\jackson-annotations\2.15.3\*"
$cp += "$repo\com\fasterxml\jackson\core\jackson-core\2.15.3\*"
$cp += "$repo\org\projectlombok\lombok\1.18.30\*"
$cp += "$repo\org\slf4j\slf4j-api\2.0.9\*"
$cp += "$repo\com\baomidou\mybatis-plus\3.5.5\*"
$cp += "$repo\com\baomidou\mybatis-plus-annotation\3.5.5\*"
$cp += "$repo\com\baomidou\mybatis-plus-core\3.5.5\*"
$cp += "$repo\com\baomidou\mybatis-plus-extension\3.5.5\*"
$cp += "$repo\com\baomidou\mybatis-plus-boot-starter\3.5.5\*"
$cp += "$repo\org\springframework\amqp\spring-amqp\3.1.0\*"
$cp += "$repo\org\springframework\amqp\spring-rabbit\3.1.0\*"
$cp += "$repo\org\mybatis\mybatis\3.5.15\*"
$cp += "$repo\org\mybatis\mybatis-spring\2.1.2\*"
$cp += "$repo\org\springframework\spring-tx\6.1.1\*"
$cp += "$repo\jakarta\annotation\jakarta.annotation-api\2.1.1\*"
$cp += "$repo\org\springframework\boot\spring-boot\3.2.0\*"
$cp += "$repo\org\springframework\boot\spring-boot-autoconfigure\3.2.0\*"
$cp += "$repo\org\springframework\cloud\spring-cloud-commons\4.1.0\*"
$classpath = $cp -join ";"
$srcBase = "d:\traeitem\labWork\LIS-clinical-lab-solution\lis-system\lis-service\lis-service-integration\src\main\java\com\lis\integration"
$outDir = "d:\traeitem\labWork\LIS-clinical-lab-solution\lis-system\lis-service\lis-service-integration\target\classes"
$srcFiles = @(
    "$srcBase\dto\FeeItem.java",
    "$srcBase\dto\FeeBilling.java",
    "$srcBase\adapter\HisAdapter.java",
    "$srcBase\adapter\UniversalRestAdapter.java",
    "$srcBase\adapter\HL7v2Adapter.java",
    "$srcBase\adapter\FhirAdapter.java",
    "$srcBase\controller\IntegrationController.java"
)
& javac -encoding UTF-8 -d $outDir -cp "$classpath" $srcFiles 2>&1
Write-Host "Exit code: $LASTEXITCODE"
